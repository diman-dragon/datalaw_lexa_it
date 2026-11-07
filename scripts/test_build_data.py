#!/usr/bin/env python3
"""Тесты пайплайна данных: python3 scripts/test_build_data.py"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_data as b  # noqa: E402


class Helpers(unittest.TestCase):
    def test_fnv1a_golden(self) -> None:
        # те же значения проверяются в CoreTest.kt — шардирование обязано совпадать
        self.assertEqual(b.fnv1a(""), 2166136261)
        self.assertEqual(b.fnv1a("ciao"), 4153443133)
        self.assertEqual(b.fnv1a("casa"), 2670018301)
        self.assertEqual(b.fnv1a("caffè"), 970764874)
        self.assertEqual(b.fnv1a("привет"), 2168984447)
        self.assertEqual(b.shard_of("casa", 512), 253)
        self.assertEqual(b.shard_of("caffè", 512), 74)
        self.assertEqual(b.shard_of("привет", 128), 127)

    def test_normalization(self) -> None:
        self.assertEqual(b.norm_it("  “Cos’è  così!” "), "cos'è così")
        self.assertEqual(b.norm_it("Perché"), "perché", "диакритика сохраняется")
        self.assertEqual(b.norm_ru("  Моро́женое, "), "мороженое")
        self.assertEqual(b.norm_ru("Ёж"), "еж")

    def test_gloss_terms(self) -> None:
        self.assertEqual(
            b.gloss_terms("жилище; домашний очаг (о семье)"),
            [("жилище", None), ("домашний очаг", "о семье")],
        )
        self.assertEqual(
            b.gloss_terms("привет (при встрече); пока (при прощании)"),
            [("привет", "при встрече"), ("пока", "при прощании")],
        )
        self.assertEqual(b.gloss_terms("очень длинное описание без короткого перевода слова вообще"), [])
        self.assertEqual(b.gloss_terms("English only"), [])

    def test_form_gloss(self) -> None:
        self.assertEqual(b.lemma_from_form_gloss("1-е лицо единственного числа настоящего времени глагола mangiare", "mangio"), "mangiare")
        self.assertEqual(b.lemma_from_form_gloss("форма множественного числа существительного casa.", "case"), "casa")
        self.assertIsNone(b.lemma_from_form_gloss("дом, здание", "casa"))
        self.assertIsNone(b.lemma_from_form_gloss("форма слова casa", "casa"), "лемма совпала с ключом")

    def test_lemma_matcher(self) -> None:
        entries = {
            "mangiare": {"m": [{"t": "verb"}]},
            "vendere": {"m": [{"t": "verb"}]},
            "venire": {"m": [{"t": "verb"}]},
            "casa": {"m": [{"t": "noun"}]},
            "libro": {"m": [{"t": "noun"}]},
            "mangio": {"r": ["mangiare"]},
        }
        m = b.LemmaMatcher(entries, {"mangio": "mangiare"})
        self.assertIn("mangiare", m.lookup("mangiamo"))
        self.assertIn("mangiare", m.lookup("mangiato"))
        self.assertIn("mangiare", m.lookup("mangio"))
        self.assertIn("mangiare", m.lookup("mangi"), "tu mangi")
        self.assertIn("mangiare", m.lookup("mangeremo"), "основа без -i- в будущем времени")
        self.assertIn("casa", m.lookup("case"))
        self.assertIn("libro", m.lookup("libri"))
        self.assertNotIn("casa", m.lookup("casino"), "основа + одна гласная, не больше")
        self.assertEqual(m.lookup("vendiamo"), ("vendere",), "vendiamo — не от venire")


class Sample(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tmp = tempfile.TemporaryDirectory()
        cls.out = Path(cls.tmp.name) / "data"
        b.main(["--sample", "--out", str(cls.out)])
        cls.entries: dict = {}
        for f in (cls.out / "e").glob("*.json"):
            cls.entries.update(json.loads(f.read_text(encoding="utf-8")))

    @classmethod
    def tearDownClass(cls) -> None:
        cls.tmp.cleanup()

    def shard_ok(self, folder: str, count: int, key: str) -> dict:
        path = self.out / folder / f"{b.shard_of(key, count):03d}.json"
        self.assertTrue(path.exists(), f"нет шарда для {key}")
        return json.loads(path.read_text(encoding="utf-8"))

    def test_meta_and_lists(self) -> None:
        meta = json.loads((self.out / "meta.json").read_text(encoding="utf-8"))
        self.assertEqual(meta["schema"], 1)
        self.assertEqual(meta["lang"], "it")
        self.assertEqual(meta["words"], 8)
        self.assertEqual(meta["forms"], 5)  # mangio (своя статья) + case, mangia, mangiato, ragazze (поле forms)
        words = (self.out / "words.txt").read_text(encoding="utf-8").splitlines()
        keys = [w.split("\t")[0] for w in words]
        self.assertEqual(keys, sorted(keys))
        self.assertIn("ciao", keys)
        self.assertIn("caffè", keys)
        self.assertNotIn("ostrogoto", keys, "редкое слово отсеяно по частоте")
        self.assertNotIn("zzyxz", keys)
        self.assertNotIn("123", keys, "некорректный ключ")
        self.assertNotIn("дом", keys, "чужой язык")
        self.assertNotIn("haus", keys, "чужой язык")
        self.assertNotIn("niente", keys, "толкование не по-русски")

    def test_manifest(self) -> None:
        manifest = json.loads((self.out / "manifest.json").read_text(encoding="utf-8"))
        meta = json.loads((self.out / "meta.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["version"], meta["built"])
        paths = [p for p, _sz in manifest["files"]]
        self.assertEqual(len(paths), len(set(paths)))
        for required in ("meta.json", "words.txt", "rwords.txt"):
            self.assertIn(required, paths)
        self.assertNotIn("manifest.json", paths)
        self.assertEqual(manifest["total"], sum(sz for _p, sz in manifest["files"]))
        for path, size in manifest["files"]:
            self.assertEqual((self.out / path).stat().st_size, size, path)
        self.assertTrue(any(p.startswith("e/") for p in paths))

    def test_entry_shard_lookup(self) -> None:
        shard = self.shard_ok("e", b.SHARDS_E, "casa")
        self.assertIn("casa", shard)

    def test_casa(self) -> None:
        e = self.entries["casa"]
        self.assertEqual([m["t"] for m in e["m"]], ["noun"])
        senses = e["m"][0]["s"]
        self.assertEqual(senses[0]["d"], "дом, здание")
        self.assertEqual(senses[0]["e"], ["La mia casa è vicina al mare."])
        self.assertEqual(senses[0]["x"], ["Мой дом находится рядом с морем."], "перевод примера из Викисловаря")
        self.assertEqual(senses[0]["y"], ["abitazione"])
        self.assertEqual(senses[-1]["g"], ["archaic"], "устаревшие значения — в конце, пометка из «устар.»")
        self.assertEqual(e["p"][0], ["[ˈkaːza]", "other"])
        self.assertEqual(e["rel"]["der"], ["casetta"], "Thesaurus:* и русские слова отброшены")
        self.assertEqual(e["rel"]["hyper"], ["edificio"])
        self.assertEqual(e["rel"]["ant"], ["fuori"])
        first = e["tr"][0]
        self.assertEqual(first, {"pos": "noun", "g": "1", "t": ["дом", "здание"]})
        second = e["tr"][1]
        self.assertEqual(second["t"][0], "жилище")
        self.assertEqual(second["t"][1], {"w": "домашний очаг", "r": "о семье"})
        self.assertEqual(len(e["tr"]), 3, "длинное описание не стало переводом")

    def test_verb_and_gender_free_translations(self) -> None:
        e = self.entries["mangiare"]
        self.assertEqual(e["m"][0]["s"][0]["g"], ["transitive"])
        self.assertEqual(e["tr"][0]["t"], ["есть", "кушать", "питаться"])
        self.assertEqual(e["tr"][2]["t"][0], {"w": "тратить", "r": "о деньгах"})

    def test_ciao_notes_per_fragment(self) -> None:
        t = self.entries["ciao"]["tr"][0]["t"]
        self.assertEqual(t, [{"w": "привет", "r": "при встрече"}, {"w": "пока", "r": "при прощании"}])

    def test_redirects(self) -> None:
        self.assertEqual(
            self.entries["mangio"],
            {"w": "mangio", "z": 35, "r": ["mangiare"], "rg": "1-е лицо единственного числа настоящего времени глагола mangiare"},
        )
        self.assertEqual(self.entries["case"], {"w": "case", "z": 48, "r": ["casa"]}, "форма из поля forms")
        self.assertEqual(self.entries["mangiato"]["r"], ["mangiare"])
        self.assertNotIn("mangiare", {k for k, a in self.entries.items() if "r" in a}, "лемма не redirect сама на себя")

    def test_case_merge_and_pos(self) -> None:
        e = self.entries["bianco"]
        self.assertEqual(e["w"], "bianco")
        self.assertEqual([m["t"] for m in e["m"]], ["adjective", "proper noun"])
        self.assertNotIn("Bianco", self.entries)
        self.assertEqual([g["pos"] for g in e["tr"]], ["adjective"], "у имени собственного описательное толкование — не перевод")
        g = self.entries["gelato"]
        self.assertEqual({m["t"] for m in g["m"]}, {"noun", "adjective"})

    def test_accents_kept(self) -> None:
        self.assertIn("caffè", self.entries)
        self.assertEqual(self.entries["caffè"]["tr"][0]["t"][0], {"w": "кофе", "r": "напиток"})

    def test_reverse_index(self) -> None:
        rev = self.shard_ok("r", b.SHARDS_R, "привет")
        self.assertEqual(rev["привет"], ["ciao"])
        rev = self.shard_ok("r", b.SHARDS_R, "мороженое")
        self.assertEqual(rev["мороженое"], ["gelato"])
        lines = (self.out / "rwords.txt").read_text(encoding="utf-8").splitlines()
        self.assertIn("дом\t54", lines)
        self.assertIn("замерзший\t39\tзамёрзший", lines, "ё сохраняется для показа")

    def test_sentences(self) -> None:
        s = self.shard_ok("s", b.SHARDS_S, "casa")
        pairs = s["casa"]
        self.assertIn(["Abbiamo comprato una bella casa vicino al mare.", "Мы купили красивый дом у моря."], pairs)
        texts = [p[0] for p in pairs]
        self.assertIn("Le case di questa città sono molto antiche.", texts, "форма «case» привязана к casa")
        s = self.shard_ok("s", b.SHARDS_S, "mangiare")
        texts = [p[0] for p in s["mangiare"]]
        self.assertIn("Lui mangia sempre troppo velocemente a cena.", texts, "спряжённая форма привязана к инфинитиву")
        self.assertIn("Ha mangiato tutto il gelato che c'era in casa.", texts, "причастие")


if __name__ == "__main__":
    unittest.main(verbosity=2)
