#!/usr/bin/env python3
"""Сборка офлайн-словаря для приложения (итальянский → русский, значения, связи слов, примеры).

Источники (все открытые):
  * Русский Викисловарь через kaikki.org (wiktextract, редакция «ru»): итальянские слова с русскими
    толкованиями, примерами, IPA, связями слов, формами.  CC BY-SA
  * wordfreq: частотность (Zipf) — выбор слов и ранжирование подсказок.  CC BY-SA
  * Tatoeba: пары предложений IT↔RU для примеров.  CC BY 2.0 FR

Выход — шардированные JSON в public/data (грузятся приложением через сеть либо из офлайн-пакета):
  meta.json          сведения о сборке
  manifest.json      список файлов и размеров (для загрузки «в офлайн» из приложения)
  words.txt          «слово \\t zipf*10» (отсортирован) — поиск по префиксу и исправление опечаток
  rwords.txt         «русский термин \\t оценка [\\t написание с ё]» — подсказки для русского ввода
  e/NNN.json         статьи: { слово: статья }
  r/NNN.json         обратный индекс: { русский термин: [итальянские слова] }
  s/NNN.json         предложения: { слово: [[it, ru], ...] }

Запуск:
  python3 scripts/build_data.py --download                 # полная сборка (качает ~2 ГБ, 15–30 мин)
  python3 scripts/build_data.py --sample                   # крошечный образец для разработки и тестов
  python3 scripts/build_data.py --kaikki f.jsonl[.gz] --tatoeba-ita i.tsv.bz2 \\
        --tatoeba-rus r.tsv.bz2 --tatoeba-links links.tar.bz2
"""
from __future__ import annotations

import argparse
import bz2
import gzip
import io
import json
import re
import shutil
import sys
import tarfile
import time
import unicodedata
import urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import IO, Any, Callable, Iterable, Iterator

SCHEMA = 1
LANG = "it"  # язык слов словаря (код wordfreq и lang_code в Викисловаре)
SHARDS_E = 512
SHARDS_R = 128
SHARDS_S = 256

ROOT = Path(__file__).resolve().parent.parent
SAMPLE = Path(__file__).resolve().parent / "sample"

# Первый доступный адрес используется; можно переопределить флагами.
# Это выгрузка РУССКОГО Викисловаря: в ней есть слова всех языков, нужные — с lang_code == "it".
KAIKKI_URLS = [
    "https://kaikki.org/dictionary/downloads/ru/ru-extract.jsonl.gz",
    "https://kaikki.org/dictionary/downloads/ru/ru-extract.jsonl",
]
TATOEBA_ITA = "https://downloads.tatoeba.org/exports/per_language/ita/ita_sentences.tsv.bz2"
TATOEBA_RUS = "https://downloads.tatoeba.org/exports/per_language/rus/rus_sentences.tsv.bz2"
TATOEBA_LINKS = "https://downloads.tatoeba.org/exports/links.tar.bz2"

# ───────────────────────── Общие функции ─────────────────────────


def fnv1a(text: str) -> int:
    """FNV-1a 32 бит по UTF-8 — должен совпадать с core/Hash.kt в приложении."""
    h = 0x811C9DC5
    for b in text.encode("utf-8"):
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def shard_of(key: str, count: int) -> int:
    return fnv1a(key) % count


_SPACE = re.compile(r"\s+")
_EDGE = re.compile(r"^[\s\"'“”«»(\[]+|[\s\"'“”«»)\].,;:!?]+$")


def norm_it(text: str) -> str:
    """Ключ итальянского слова — как normalizeIt() в приложении (диакритика сохраняется: è ≠ e)."""
    t = unicodedata.normalize("NFC", text).replace("‘", "'").replace("’", "'")
    t = _SPACE.sub(" ", t).strip()
    t = _EDGE.sub("", t)
    return t.lower()


def norm_ru(text: str) -> str:
    """Ключ русского термина — как normalizeRu(): без ударений, ё→е, нижний регистр."""
    t = unicodedata.normalize("NFC", text).replace("́", "").replace("̀", "")
    t = _SPACE.sub(" ", t).strip()
    t = _EDGE.sub("", t)
    return t.lower().replace("ё", "е")


def log(*args: Any) -> None:
    print(*args, file=sys.stderr, flush=True)


def open_text(path: Path) -> IO[str]:
    name = path.name.lower()
    if name.endswith(".bz2"):
        return io.TextIOWrapper(bz2.open(path, "rb"), encoding="utf-8", errors="replace")
    if name.endswith(".gz"):
        return io.TextIOWrapper(gzip.open(path, "rb"), encoding="utf-8", errors="replace")
    return open(path, "r", encoding="utf-8", errors="replace")


def download(url: str, dest: Path, retries: int = 3) -> Path:
    if dest.exists() and dest.stat().st_size > 0:
        log(f"  кэш: {dest.name}")
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    for attempt in range(1, retries + 1):
        try:
            log(f"  загрузка {url}")
            req = urllib.request.Request(url, headers={"User-Agent": "datalaw_lexa-data-build/1.0"})
            with urllib.request.urlopen(req, timeout=60) as resp, open(tmp, "wb") as out:
                total = int(resp.headers.get("Content-Length") or 0)
                done = 0
                mark = 0
                while True:
                    chunk = resp.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
                    done += len(chunk)
                    if done - mark >= 200 << 20:
                        mark = done
                        log(f"    {done >> 20} МБ" + (f" / {total >> 20} МБ" if total else ""))
            tmp.rename(dest)
            return dest
        except Exception as e:  # noqa: BLE001 — сеть нестабильна, повторяем
            log(f"  ошибка ({attempt}/{retries}): {e}")
            time.sleep(3 * attempt)
    raise RuntimeError(f"Не удалось скачать {url}")


# ───────────────────────── Частотность ─────────────────────────


def make_zipf(required: bool) -> tuple[Callable[[str], float], bool]:
    try:
        from wordfreq import zipf_frequency  # type: ignore[import-not-found]

        return (lambda w: float(zipf_frequency(w, LANG))), True
    except ImportError:
        if required:
            raise SystemExit("wordfreq не установлен: pip install wordfreq")
        log("ВНИМАНИЕ: wordfreq не найден — частотность не учитывается (только для образца/разработки).")
        return (lambda _w: 0.0), False


# ───────────────────────── Разбор статей Wiktionary (русская редакция) ─────────────────────────

KEY_RE = re.compile(r"^[a-zà-ÿ][a-zà-ÿ' .\-]{0,39}$")
CYR_RE = re.compile(r"[Ѐ-ӿ]")
LATIN_RE = re.compile(r"[A-Za-zÀ-ÿ]")

LOW_PRIORITY_TAGS = {"obsolete", "archaic", "rare", "dated", "historical", "nonstandard", "dialectal"}
KEEP_TAGS = {
    "informal", "slang", "colloquial", "formal", "literary", "vulgar", "offensive", "derogatory",
    "archaic", "obsolete", "dated", "rare", "figuratively", "humorous", "poetic", "technical",
    "transitive", "intransitive", "countable", "uncountable",
}
# Русские пометки Викисловаря (raw_tags) → английские ключи, которые знает приложение (I18n.kt)
RAW_TAGS_RU = {
    "разг.": "informal", "разговорное": "informal", "прост.": "informal", "просторечное": "informal",
    "устар.": "archaic", "устаревшее": "archaic", "редк.": "rare", "редкое": "rare",
    "перен.": "figuratively", "переносное": "figuratively", "книжн.": "literary", "книжное": "literary",
    "вульг.": "vulgar", "вульгарное": "vulgar", "груб.": "vulgar", "шутл.": "humorous", "шутливое": "humorous",
    "поэт.": "poetic", "поэтическое": "poetic", "техн.": "technical", "уничижит.": "derogatory",
    "пренебр.": "derogatory", "неодобр.": "derogatory", "жарг.": "slang", "сленг": "slang",
    "офиц.": "formal", "официальное": "formal", "диал.": "dialectal", "диалектное": "dialectal",
    "перех.": "transitive", "неперех.": "intransitive",
}
REL_FIELDS = {
    "synonyms": "syn", "antonyms": "ant", "hypernyms": "hyper", "hyponyms": "hypo",
    "related": "rel", "derived": "der", "coordinate_terms": "coord",
}
SKIP_POS = {"character", "symbol", "punct"}
POS_MAP = {
    "adj": "adjective", "adv": "adverb", "intj": "interjection", "prep": "preposition",
    "conj": "conjunction", "pron": "pronoun", "det": "determiner", "num": "numeral",
    "name": "proper noun", "abbrev": "abbreviation", "prep_phrase": "prepositional phrase",
    "adv_phrase": "adverbial phrase", "verb_phrase": "verb phrase",
}

CAPS = {
    "senses_per_pos": 8,
    "senses_total": 14,
    "examples_per_sense": 2,
    "tr_groups": 14,
    "tr_terms": 10,
    "rel_words": 24,
    "sense_rel": 8,
    "ipa": 2,
}

# «значение не указано» и подобные заглушки Викисловаря — не толкования
PLACEHOLDER_RE = re.compile(r"^(?:значение (?:не указано|отсутствует)|нет определения|отсутствует|см\.)", re.I)
# Толкование формы слова: «форма множественного числа существительного casa», «1-е лицо … от mangiare»
FORM_GLOSS_RE = re.compile(
    r"^(?:форма|множественное число|мн\. ?ч\.|женский род|мужской род|уменьшительн|увеличительн|"
    r"превосходная степень|сравнительная степень|\d-?е лицо|причастие|деепричастие|герундий|"
    r"повелительное|императив|сослагательное|условное|прошедшее|будущее|настоящее|инфинитив|"
    r"альтернативн|устаревшая форма|вариант)",
    re.I,
)
LATIN_TAIL_RE = re.compile(r"([A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'\-]{0,38})\.?$")
PAREN_RE = re.compile(r"\([^()]*\)|\[[^\[\]]*\]|<[^<>]*>")


def clean_text(s: Any, limit: int | None = None) -> str:
    if not isinstance(s, str):
        return ""
    s = _SPACE.sub(" ", s).strip()
    if limit and len(s) > limit:
        s = s[: limit - 1].rstrip() + "…"
    return s


def rel_words(items: Any) -> list[str]:
    """Связанные слова: только итальянские (латиница), без «Thesaurus:» и служебных меток."""
    out: list[str] = []
    for it in items or []:
        w = it.get("word") if isinstance(it, dict) else it
        if not isinstance(w, str):
            continue
        w = clean_text(w)
        if not w or len(w) > 30 or w.startswith("Thesaurus:") or ":" in w:
            continue
        if CYR_RE.search(w) or not LATIN_RE.search(w):
            continue
        if w not in out:
            out.append(w)
    return out


def sound_list(sounds: Any) -> list[tuple[str, str]]:
    """Транскрипции IPA. У итальянского один «акцент», поэтому второе поле всегда other."""
    seen: set[str] = set()
    got: list[tuple[str, str]] = []
    for s in sounds or []:
        if not isinstance(s, dict):
            continue
        ipa = clean_text(s.get("ipa"))
        if not ipa or ipa in seen or CYR_RE.search(ipa):
            continue
        seen.add(ipa)
        got.append((ipa, "other"))
    return got[: CAPS["ipa"]]


def clean_ru(word: Any) -> str | None:
    if not isinstance(word, str):
        return None
    w = unicodedata.normalize("NFC", word).replace("́", "").replace("̀", "")
    w = _SPACE.sub(" ", w).strip()
    if not w or len(w) > 60 or not CYR_RE.search(w) or "{{" in w or "[[" in w:
        return None
    return w


def sense_tags(s: dict[str, Any]) -> list[str]:
    tags = [t for t in (s.get("tags") or []) if isinstance(t, str)]
    for raw in s.get("raw_tags") or []:
        if isinstance(raw, str):
            mapped = RAW_TAGS_RU.get(raw.strip().lower())
            if mapped and mapped not in tags:
                tags.append(mapped)
    return tags


def gloss_terms(gloss: str) -> list[tuple[str, str | None]]:
    """Толкование русского Викисловаря → [(короткий перевод, пояснение)].

    «жилище; домашний очаг (о семье)» → [(жилище, None), (домашний очаг, о семье)].
    Пояснение в скобках относится к своему фрагменту (между «;»).
    Длинные описательные фрагменты (больше 4 слов) не считаются переводами и отбрасываются.
    """
    out: list[tuple[str, str | None]] = []
    seen: set[str] = set()
    for fragment in gloss.split(";"):
        note = None
        m = re.search(r"\(([^()]{2,30})\)", fragment)
        if m and CYR_RE.search(m.group(1)):
            note = m.group(1).strip()
        text = PAREN_RE.sub(" ", fragment)
        for part in text.split(","):
            part = part.strip(' .:—-–"«»')
            if not part or len(part.split()) > 4 or len(part) > 40:
                continue
            w = clean_ru(part)
            if w and w.lower() not in seen:
                seen.add(w.lower())
                out.append((w, note))
    return out


def lemma_from_form_gloss(gloss: str, key: str) -> str | None:
    """«форма … casa» → ключ леммы. Эвристика на случай, когда в выгрузке нет form_of."""
    if not FORM_GLOSS_RE.match(gloss):
        return None
    m = LATIN_TAIL_RE.search(gloss.strip())
    if not m:
        return None
    lemma = norm_it(m.group(1))
    return lemma if lemma != key and KEY_RE.match(lemma) else None


def example_pair(ex: Any) -> tuple[str, str] | None:
    if not isinstance(ex, dict):
        return None
    text = clean_text(ex.get("text"))
    if not text or len(text) > 160 or CYR_RE.search(text) or not LATIN_RE.search(text):
        return None
    tr = clean_text(ex.get("translation"), 200)
    return text, (tr if CYR_RE.search(tr) else "")


def process_entry(e: dict[str, Any]) -> dict[str, Any] | None:
    """Одна запись wiktextract (русская редакция) → промежуточный словарь (или None, если не нужна)."""
    if e.get("lang_code") != LANG:
        return None
    word = e.get("word")
    pos = e.get("pos") or ""
    if not isinstance(word, str) or pos in SKIP_POS:
        return None
    pos = POS_MAP.get(pos, pos).replace("_", " ")
    word = word.strip()
    key = norm_it(word)
    if not KEY_RE.match(key):
        return None

    senses_raw = [s for s in (e.get("senses") or []) if isinstance(s, dict)]
    lemma_senses: list[dict[str, Any]] = []
    tr_groups: list[dict[str, Any]] = []
    redirect: tuple[list[str], str] | None = None

    for s in senses_raw:
        glosses = s.get("glosses") or []
        gloss = clean_text(glosses[-1] if glosses else "", 400)
        if not gloss or "no-gloss" in (s.get("tags") or []) or PLACEHOLDER_RE.match(gloss):
            continue
        if not CYR_RE.search(gloss):
            continue  # толкование не по-русски — не наш случай
        form = s.get("form_of") or s.get("alt_of")
        lemmas: list[str] = []
        if form:
            lemmas = [norm_it(clean_text(f.get("word"))) for f in form if isinstance(f, dict) and f.get("word")]
            lemmas = [x for x in lemmas if x and x != key]
        else:
            guess = lemma_from_form_gloss(gloss, key)
            if guess:
                lemmas = [guess]
        if lemmas:
            if redirect is None:
                redirect = (lemmas[:2], gloss)
            continue

        tags = sense_tags(s)
        sense: dict[str, Any] = {"d": gloss}
        exs: list[str] = []
        trs: list[str] = []
        for ex in s.get("examples") or []:
            pair = example_pair(ex)
            if not pair:
                continue
            exs.append(pair[0])
            trs.append(pair[1])
            if len(exs) >= CAPS["examples_per_sense"]:
                break
        if exs:
            sense["e"] = exs
            if any(trs):
                sense["x"] = trs
        syn = rel_words(s.get("synonyms"))[: CAPS["sense_rel"]]
        ant = rel_words(s.get("antonyms"))[: CAPS["sense_rel"]]
        if syn:
            sense["y"] = syn
        if ant:
            sense["a"] = ant
        shown = [t for t in tags if t in KEEP_TAGS][:3]
        if shown:
            sense["g"] = shown
        sense["_low"] = 1 if any(t in LOW_PRIORITY_TAGS for t in tags) else 0
        lemma_senses.append(sense)

        # Переводы по значениям: короткие фрагменты русского толкования
        terms = gloss_terms(gloss)
        if pos == "proper noun":  # у имён собственных толкование описательное — оставляем только однословные
            terms = [(w, n) for w, n in terms if " " not in w]
        if terms:
            group: dict[str, Any] = {"pos": pos or "other", "g": str(len(lemma_senses)), "t": []}
            for w, note in terms[: CAPS["tr_terms"]]:
                group["t"].append({"w": w, "r": note} if note else {"w": w})
            tr_groups.append(group)

    if not lemma_senses and redirect is None:
        return None

    rel: dict[str, list[str]] = {}
    for field, name in REL_FIELDS.items():
        words = rel_words(e.get(field))
        for s in senses_raw:
            for w in rel_words(s.get(field)):
                if w not in words:
                    words.append(w)
        if words:
            rel[name] = words

    forms: list[str] = []
    for f in e.get("forms") or []:
        w = f.get("form") if isinstance(f, dict) else None
        if not isinstance(w, str):
            continue
        fk = norm_it(w)
        if fk and fk != key and " " not in fk and KEY_RE.match(fk) and fk not in forms:
            forms.append(fk)

    return {
        "word": word,
        "key": key,
        "pos": pos or "other",
        "senses": lemma_senses,
        "redirect": redirect,
        "ipa": sound_list(e.get("sounds")),
        "tr": tr_groups,
        "rel": rel,
        "et": clean_text(e.get("etymology_text"), 420),
        "forms": forms if lemma_senses else [],
    }


class Acc:
    """Накопитель статей одного ключа (разные этимологии и регистры сливаются)."""

    __slots__ = ("display", "pos_order", "senses", "ipa", "tr", "rel", "et", "redirect", "has_ru")

    def __init__(self) -> None:
        self.display = ""
        self.pos_order: list[str] = []
        self.senses: dict[str, list[dict[str, Any]]] = {}
        self.ipa: list[tuple[str, str]] = []
        self.tr: list[dict[str, Any]] = []
        self.rel: dict[str, list[str]] = {}
        self.et = ""
        self.redirect: tuple[list[str], str] | None = None
        self.has_ru = False

    def add(self, p: dict[str, Any]) -> None:
        word: str = p["word"]
        # отображаем строчную форму, если она есть
        if not self.display or (word == word.lower() and self.display != self.display.lower()):
            self.display = word
        if p["senses"]:
            pos = p["pos"]
            if pos not in self.senses:
                self.pos_order.append(pos)
                self.senses[pos] = []
            self.senses[pos].extend(p["senses"])
        if p["redirect"] and not self.redirect:
            self.redirect = p["redirect"]
        for ipa in p["ipa"]:
            if ipa not in self.ipa:
                self.ipa.append(ipa)
        if p["tr"]:
            self.has_ru = True
            self.tr.extend(p["tr"])
        for name, words in p["rel"].items():
            cur = self.rel.setdefault(name, [])
            for w in words:
                if w not in cur:
                    cur.append(w)
        if p["et"] and not self.et:
            self.et = p["et"]


def finalize(acc: Acc, zipf10: int) -> dict[str, Any] | None:
    """Накопитель → итоговая статья для JSON."""
    meanings: list[dict[str, Any]] = []
    total = 0
    for pos in acc.pos_order:
        senses = sorted(acc.senses[pos], key=lambda s: s["_low"])  # стабильная: редкие — в конец
        seen: set[str] = set()
        uniq: list[dict[str, Any]] = []
        for s in senses:
            low = s["d"].lower()
            if low in seen:
                continue
            seen.add(low)
            uniq.append({k: v for k, v in s.items() if k != "_low"})
        uniq = uniq[: CAPS["senses_per_pos"]]
        if total + len(uniq) > CAPS["senses_total"]:
            uniq = uniq[: max(0, CAPS["senses_total"] - total)]
        if uniq:
            meanings.append({"t": pos, "s": uniq})
            total += len(uniq)

    if not meanings:
        if acc.redirect:
            lemmas, gloss = acc.redirect
            return {"w": acc.display, "z": zipf10, "r": lemmas, "rg": clean_text(gloss, 160)}
        return None

    out: dict[str, Any] = {"w": acc.display, "z": zipf10, "m": meanings}
    if acc.ipa:
        out["p"] = [list(p) for p in acc.ipa[: CAPS["ipa"]]]
    if acc.tr:
        out["tr"] = merge_tr(acc.tr)
    if acc.rel:
        out["rel"] = {k: v[: CAPS["rel_words"]] for k, v in acc.rel.items()}
    if acc.et:
        out["et"] = acc.et
    return out


def merge_tr(groups: list[dict[str, Any]]) -> list[dict[str, Any]]:
    merged: dict[tuple[str, str], dict[str, Any]] = {}
    for g in groups:
        k = (g.get("pos", ""), (g.get("g") or "").lower())
        cur = merged.get(k)
        if not cur:
            merged[k] = {**g, "t": list(g["t"])}
            continue
        for t in g["t"]:
            if all(x["w"] != t["w"] for x in cur["t"]):
                cur["t"].append(t)
    out = list(merged.values())[: CAPS["tr_groups"]]
    for g in out:
        g["t"] = g["t"][: CAPS["tr_terms"]]
        # компактно: термин без пометок — просто строка
        g["t"] = [t["w"] if set(t) == {"w"} else t for t in g["t"]]
    return out


# ───────────────────────── Чтение дампа ─────────────────────────


def iter_jsonl(path: Path) -> Iterator[dict[str, Any]]:
    with open_text(path) as f:
        for n, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(obj, dict):
                yield obj
            if n % 200_000 == 0:
                log(f"    прочитано строк: {n:,}")


def build_accumulators(
    entries: Iterable[dict[str, Any]], zipf: Callable[[str], float], have_freq: bool
) -> tuple[dict[str, Acc], dict[str, float], dict[str, set[str]]]:
    accs: dict[str, Acc] = {}
    zcache: dict[str, float] = {}
    form_links: dict[str, set[str]] = defaultdict(set)  # форма → леммы (из поля forms)
    kept = 0
    for e in entries:
        p = process_entry(e)
        if p is None:
            continue
        key = p["key"]
        z = zcache.get(key)
        if z is None:
            z = zipf(key)
            zcache[key] = z
        has_ru = bool(p["tr"])
        # ранний отсев: мусорные редкие слова без перевода
        if have_freq and z < 1.0 and not has_ru:
            continue
        if have_freq and z < 0.5:
            continue
        acc = accs.get(key)
        if acc is None:
            acc = accs[key] = Acc()
        acc.add(p)
        for f in p["forms"]:
            form_links[f].add(key)
        kept += 1
    log(f"  статей принято: {kept:,}, ключей: {len(accs):,}")
    return accs, zcache, form_links


# ───────────────────────── Tatoeba ─────────────────────────


def read_sentences(path: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    with open_text(path) as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                out[parts[0]] = parts[2]
    return out


def iter_links(path: Path) -> Iterator[tuple[str, str]]:
    name = path.name.lower()
    if name.endswith(".tar.bz2") or name.endswith(".tar.gz") or name.endswith(".tar"):
        with tarfile.open(path, "r:*") as tar:
            for member in tar:
                if member.isfile() and member.name.endswith(("links.csv", "links.tsv")):
                    f = tar.extractfile(member)
                    if f is None:
                        continue
                    for raw in io.TextIOWrapper(f, encoding="utf-8", errors="replace"):
                        a = raw.rstrip("\n").split("\t")
                        if len(a) >= 2:
                            yield a[0], a[1]
                    return
        return
    with open_text(path) as f:
        for raw in f:
            a = raw.rstrip("\n").split("\t")
            if len(a) >= 2:
                yield a[0], a[1]


# Слова предложения: апостроф разделяет («l'amico» → l, amico)
TOKEN_RE = re.compile(r"[a-zà-ÿ]+")

# Окончания спряжения по классам инфинитива: основа = инфинитив без -are/-ere/-ire
VERB_ENDINGS: dict[str, frozenset[str]] = {
    "are": frozenset(
        "o i a amo iamo ate iate ano ino ando ato ata ati ate avo avi ava avamo avate avano erò erai erà eremo "
        "erete eranno ai asti ò ammo aste arono erei eresti erebbe eremmo ereste erebbero ante e "
        "hi hiamo hino herò herai herà heremo herete heranno herei heresti herebbe heremmo hereste herebbero".split() + [""]
    ),
    "ere": frozenset(
        "o i e a iamo iate ete ano ono endo uto uta uti ute evo evi eva evamo evate evano erò erai erà eremo "
        "erete eranno ei etti esti ette emmo este erono ettero erei eresti erebbe eremmo ereste erebbero ente".split() + [""]
    ),
    "ire": frozenset(
        "o i e a iamo iate ite ano ono endo ito ita iti ite ivo ivi iva ivamo ivate ivano irò irai irà iremo "
        "irete iranno ii isti ì immo iste irono irei iresti irebbe iremmo ireste irebbero isco isci isce iscono "
        "ente".split() + [""]
    ),
}
NOUN_VOWELS = "aeio"


class LemmaMatcher:
    """Грубое сопоставление словоформы предложения с леммой (без морфологического анализатора).

    * точное совпадение с ключом словаря и формы-перенаправления;
    * глаголы: основа инфинитива + типичное окончание спряжения (mangiare → mangio, mangiato, mangeremo…);
    * существительные и прилагательные: основа + одна гласная (casa → case, libro → libri).
    Неправильные глаголы так не находятся — их закрывают формы из Викисловаря (redirects).
    """

    def __init__(self, entries: dict[str, dict[str, Any]], redirects: dict[str, str]) -> None:
        self.keys = set(entries)
        self.redirects = redirects
        self.verb: dict[str, list[tuple[str, str]]] = defaultdict(list)  # основа → [(класс, лемма)]
        self.vowel: dict[str, list[str]] = defaultdict(list)  # основа → [лемма]
        for key, art in entries.items():
            if "r" in art or " " in key or "'" in key or "-" in key:
                continue
            is_verb = any(m.get("t") == "verb" for m in art.get("m", []))
            if is_verb and len(key) >= 6 and key[-3:] in VERB_ENDINGS:
                stem = key[:-3]
                self.verb[stem].append((key[-3:], key))
                if key.endswith("iare") and len(stem) >= 4:  # mangiare: mangi-amo, но mang-i, mang-erò
                    self.verb[stem[:-1]].append((key[-3:], key))
            elif not is_verb and len(key) >= 4 and key[-1] in NOUN_VOWELS:
                self.vowel[key[:-1]].append(key)
        self._cache: dict[str, tuple[str, ...]] = {}

    def lookup(self, token: str) -> tuple[str, ...]:
        hit = self._cache.get(token)
        if hit is not None:
            return hit
        out: list[str] = []
        if token in self.keys:
            out.append(token)
        if token in self.redirects:
            out.append(self.redirects[token])
        if len(token) >= 4:
            for cut in range(0, 7):
                stem = token[: len(token) - cut]
                if len(stem) < 3:
                    break
                for klass, lemma in self.verb.get(stem, ()):
                    if token[len(stem):] in VERB_ENDINGS[klass]:
                        out.append(lemma)
            if token[-1] in NOUN_VOWELS:
                out.extend(self.vowel.get(token[:-1], ()))
        res = tuple(dict.fromkeys(out))
        self._cache[token] = res
        return res


def sentence_index(
    ita: Path, rus: Path, links: Path, entries: dict[str, dict[str, Any]], redirects: dict[str, str], per_word: int = 3
) -> dict[str, list[list[str]]]:
    log("  Tatoeba: чтение предложений…")
    it_s = read_sentences(ita)
    ru_s = read_sentences(rus)
    log(f"    ita: {len(it_s):,}  rus: {len(ru_s):,}")
    matcher = LemmaMatcher(entries, redirects)

    best: dict[str, list[tuple[float, int, str, str]]] = defaultdict(list)
    seen_pairs: set[tuple[str, str]] = set()
    n_pairs = 0
    for a, b in iter_links(links):
        if a in it_s and b in ru_s:
            pair = (a, b)
        elif b in it_s and a in ru_s:
            pair = (b, a)
        else:
            continue
        if pair in seen_pairs:
            continue
        seen_pairs.add(pair)
        text_it, text_ru = it_s[pair[0]], ru_s[pair[1]]
        if not (18 <= len(text_it) <= 120) or len(text_ru) > 160:
            continue
        n_pairs += 1
        score = abs(len(text_it) - 55) / 55.0  # чем ближе к ~55 символам, тем лучше
        tokens = set(TOKEN_RE.findall(text_it.lower().replace("’", "'")))
        words: set[str] = set()
        for tok in tokens:
            words.update(matcher.lookup(tok))
        for w in words:
            lst = best[w]
            lst.append((score, len(lst), text_it, text_ru))
            if len(lst) > per_word * 4:
                lst.sort()
                del lst[per_word * 2 :]
    log(f"    пар IT↔RU: {n_pairs:,}")

    out: dict[str, list[list[str]]] = {}
    for w, lst in best.items():
        lst.sort()
        uniq: list[list[str]] = []
        for _s, _i, a, b in lst:
            if all(a != u[0] for u in uniq):
                uniq.append([a, b])
            if len(uniq) >= per_word:
                break
        if uniq:
            out[w] = uniq
    return out


# ───────────────────────── Запись результата ─────────────────────────


def dump(obj: Any) -> str:
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def write_shards(out: Path, folder: str, count: int, items: dict[str, Any]) -> int:
    buckets: dict[int, dict[str, Any]] = defaultdict(dict)
    for k, v in items.items():
        buckets[shard_of(k, count)][k] = v
    target = out / folder
    target.mkdir(parents=True, exist_ok=True)
    for idx, bucket in buckets.items():
        (target / f"{idx:03d}.json").write_text(dump(bucket), encoding="utf-8")
    return len(buckets)


def prepare_out(out: Path) -> None:
    """Готовит каталог вывода. Удаляет только прежнюю сборку словаря (или пустой каталог),
    чтобы ошибка в --out не стёрла чужие файлы."""
    out = out.resolve()
    if out.exists():
        if not out.is_dir():
            raise SystemExit(f"--out {out}: это файл, а не каталог")
        content = list(out.iterdir())
        if content and not (out / "meta.json").is_file():
            raise SystemExit(f"--out {out}: каталог не пуст и не похож на прежнюю сборку (нет meta.json) — не удаляю")
        if out in (ROOT, Path.home(), Path(out.anchor)):
            raise SystemExit(f"--out {out}: отказываюсь очищать этот каталог")
        shutil.rmtree(out)
    out.mkdir(parents=True)


def build(
    kaikki: Path,
    out: Path,
    max_words: int,
    min_zipf: float,
    zipf: Callable[[str], float],
    have_freq: bool,
    tatoeba: tuple[Path, Path, Path] | None,
) -> dict[str, Any]:
    log("Разбор Викисловаря (итальянские статьи)…")
    accs, zcache, form_links = build_accumulators(iter_jsonl(kaikki), zipf, have_freq)

    # Отбор: частотные слова + слова с русским переводом, ограничение по max_words.
    ranked: list[tuple[float, str]] = []
    for key, acc in accs.items():
        z = zcache.get(key, 0.0)
        if have_freq and z < min_zipf and not (acc.has_ru and z >= 1.5):
            continue
        ranked.append((z + (0.5 if acc.has_ru else 0.0), key))
    ranked.sort(key=lambda x: (-x[0], x[1]))
    chosen = [k for _s, k in ranked[:max_words]]
    log(f"  отобрано слов: {len(chosen):,} из {len(accs):,}")

    entries: dict[str, dict[str, Any]] = {}
    redirects: dict[str, str] = {}
    for key in chosen:
        z10 = int(round(min(max(zcache.get(key, 0.0), 0.0), 8.0) * 10))
        art = finalize(accs[key], z10)
        if art is None:
            continue
        entries[key] = art
        if "r" in art:
            redirects[key] = art["r"][0]
    # висячие перенаправления (лемма не попала в словарь) не нужны
    for key in [k for k, a in entries.items() if "r" in a and not any(r in entries for r in a["r"])]:
        del entries[key]
        redirects.pop(key, None)

    # Формы слов из поля forms лемм (mangiare → mangio, mangia, mangiato…), если у формы нет своей статьи
    extra_forms = 0
    for form, lemmas in sorted(form_links.items()):
        if form in entries:
            continue
        good = [lem for lem in sorted(lemmas) if lem in entries and "r" not in entries[lem]]
        if not good:
            continue
        z = zcache.get(form)
        if z is None:
            z = zipf(form)
            zcache[form] = z
        if have_freq and z < 1.5:
            continue  # редкие формы не раздувают словарь
        z10 = int(round(min(max(z, 0.0), 8.0) * 10))
        entries[form] = {"w": form, "z": z10, "r": good[:2]}
        redirects[form] = good[0]
        extra_forms += 1
    log(f"  форм из поля forms добавлено: {extra_forms:,}")

    # Обратный индекс RU → IT
    log("Обратный индекс RU→IT…")
    reverse: dict[str, dict[str, int]] = defaultdict(dict)
    display: dict[str, str] = {}
    for key, art in entries.items():
        for g in art.get("tr", []):
            for t in g["t"]:
                w = t if isinstance(t, str) else t["w"]
                rk = norm_ru(w)
                if not rk or len(rk) > 40:
                    continue
                reverse[rk][key] = max(reverse[rk].get(key, 0), art["z"])
                if rk != w.lower() and rk not in display:
                    display[rk] = w.lower()
    rev_out = {
        rk: [w for w, _z in sorted(m.items(), key=lambda kv: (-kv[1], kv[0]))[:40]] for rk, m in reverse.items()
    }

    sentences: dict[str, list[list[str]]] = {}
    if tatoeba:
        try:
            sentences = sentence_index(*tatoeba, entries=entries, redirects=redirects)
            sentences = {w: s for w, s in sentences.items() if w in entries and "r" not in entries[w]}
        except Exception as e:  # noqa: BLE001
            log(f"  Tatoeba пропущена: {e}")

    # Запись
    prepare_out(out)
    n_e = write_shards(out, "e", SHARDS_E, entries)
    n_r = write_shards(out, "r", SHARDS_R, rev_out)
    n_s = write_shards(out, "s", SHARDS_S, sentences) if sentences else 0

    (out / "words.txt").write_text(
        "\n".join(f"{k}\t{entries[k]['z']}" for k in sorted(entries)) + "\n", encoding="utf-8"
    )
    rlines = []
    for rk in sorted(rev_out):
        score = max(reverse[rk].values())
        rlines.append(f"{rk}\t{score}" + (f"\t{display[rk]}" if rk in display else ""))
    (out / "rwords.txt").write_text("\n".join(rlines) + "\n", encoding="utf-8")

    meta = {
        "schema": SCHEMA,
        "built": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "words": sum(1 for a in entries.values() if "r" not in a),
        "forms": sum(1 for a in entries.values() if "r" in a),
        "translated": sum(1 for a in entries.values() if "tr" in a),
        "ruTerms": len(rev_out),
        "sentences": sum(len(v) for v in sentences.values()),
        "shards": {"e": SHARDS_E, "r": SHARDS_R, "s": SHARDS_S},
        "files": {"e": n_e, "r": n_r, "s": n_s},
        "frequency": have_freq,
        "lang": LANG,
        "sources": ["Викисловарь ru (kaikki.org, CC BY-SA)", "wordfreq (CC BY-SA)", "Tatoeba (CC BY 2.0 FR)"],
    }
    (out / "meta.json").write_text(dump(meta), encoding="utf-8")

    # manifest.json — список файлов для кнопки «Скачать для офлайна» в приложении
    listing = sorted(
        (f.relative_to(out).as_posix(), f.stat().st_size) for f in out.rglob("*") if f.is_file()
    )
    manifest = {
        "version": meta["built"],
        "total": sum(sz for _p, sz in listing),
        "files": [[path, sz] for path, sz in listing],
    }
    (out / "manifest.json").write_text(dump(manifest), encoding="utf-8")
    size = sum(f.stat().st_size for f in out.rglob("*") if f.is_file())
    log(f"Готово: {meta['words']:,} слов, {meta['forms']:,} форм, {meta['ruTerms']:,} русских терминов, "
        f"{meta['sentences']:,} предложений, {size / 1e6:.1f} МБ → {out}")
    return meta


# ───────────────────────── CLI ─────────────────────────


def fetch_kaikki(cache: Path, urls: list[str]) -> Path:
    last: Exception | None = None
    for url in urls:
        try:
            return download(url, cache / Path(url).name)
        except Exception as e:  # noqa: BLE001
            last = e
            log(f"  недоступно: {url}")
    raise SystemExit(f"Не удалось скачать дамп kaikki: {last}")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Сборка офлайн-словаря datalaw_lexa_it (итальянский → русский)")
    ap.add_argument("--out", type=Path, default=ROOT / "public" / "data")
    ap.add_argument("--cache", type=Path, default=ROOT / ".data-cache")
    ap.add_argument("--download", action="store_true", help="скачать исходные дампы")
    ap.add_argument("--sample", action="store_true", help="собрать крошечный образец (без сети)")
    ap.add_argument("--kaikki", type=Path)
    ap.add_argument("--kaikki-url", action="append", default=[])
    ap.add_argument("--tatoeba-ita", type=Path)
    ap.add_argument("--tatoeba-rus", type=Path)
    ap.add_argument("--tatoeba-links", type=Path)
    ap.add_argument("--no-tatoeba", action="store_true")
    ap.add_argument("--max-words", type=int, default=90_000)
    ap.add_argument("--min-zipf", type=float, default=2.8)
    ap.add_argument("--require-wordfreq", action="store_true")
    a = ap.parse_args(argv)

    tatoeba: tuple[Path, Path, Path] | None = None

    if a.sample:
        kaikki = SAMPLE / "kaikki-sample.jsonl"
        tatoeba = (SAMPLE / "tatoeba-ita.tsv", SAMPLE / "tatoeba-rus.tsv", SAMPLE / "tatoeba-links.tsv")
        zipf, have_freq = _sample_zipf, True  # образец не зависит от wordfreq
    else:
        zipf, have_freq = make_zipf(a.require_wordfreq)
        if a.download:
            kaikki = a.kaikki or fetch_kaikki(a.cache, a.kaikki_url or KAIKKI_URLS)
        elif a.kaikki:
            kaikki = a.kaikki
        else:
            ap.error("укажите --download, --sample или --kaikki ФАЙЛ")
        if not a.no_tatoeba:
            try:
                ita = a.tatoeba_ita or (download(TATOEBA_ITA, a.cache / "ita_sentences.tsv.bz2") if a.download else None)
                rus = a.tatoeba_rus or (download(TATOEBA_RUS, a.cache / "rus_sentences.tsv.bz2") if a.download else None)
                lnk = a.tatoeba_links or (download(TATOEBA_LINKS, a.cache / "links.tar.bz2") if a.download else None)
                if ita and rus and lnk:
                    tatoeba = (ita, rus, lnk)
            except Exception as e:  # noqa: BLE001
                log(f"ВНИМАНИЕ: Tatoeba недоступна, примеры-предложения не будут собраны: {e}")

    build(kaikki, a.out, a.max_words, a.min_zipf, zipf, have_freq, tatoeba)
    return 0


_SAMPLE_Z = {
    "ciao": 5.1, "casa": 5.4, "case": 4.8, "mangiare": 4.6, "mangio": 3.5, "mangia": 4.0, "mangiato": 4.1,
    "bello": 5.0, "bianco": 4.6, "caffè": 4.4, "gelato": 3.9, "ragazza": 4.5, "ostrogoto": 1.2, "zzyxz": 0.0,
}


def _sample_zipf(w: str) -> float:
    return _SAMPLE_Z.get(w, 3.0)


if __name__ == "__main__":
    raise SystemExit(main())
