package com.fluxtion.server.plugin.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogueAndLogTest {

    @TempDir
    Path dir;

    @Test
    void placeholdersMatchOneNameSegment() throws Exception {
        Catalogue c = Catalogue.parse(SchedulerEngineTest.CATALOGUE);
        assertEquals(Map.of("strategy", "MAKER_A"), c.find("strat.MAKER_A.setMaxBookAge").params());
        assertNull(c.find("strat.A.B.setMaxBookAge"), "a placeholder never spans a dot");
        assertNull(c.find("strat.A.setMaxBookAgeX"));
        assertEquals(Map.of("feed", "marketFeed"), c.find("marketFeed.connect").params());
        assertEquals(List.of("{pricer}.setLogLevel"), c.unmatchedEntries(List.of("strat.A.setMaxBookAge", "strat.A.setPriceSource", "f.connect")));
        assertEquals("strat.A.getPriceSources", CatalogueEntry.fill("strat.{strategy}.getPriceSources", Map.of("strategy", "A")));
    }

    @Test
    void choicesCanBeReadFromPlainTextLines() throws Exception {
        Catalogue c = Catalogue.parse("""
                commands:
                  - command: "{svc}.updateTier"
                    arguments:
                      - name: tier
                        type: CHOICE
                        choicesFrom:
                          command: "{svc}.listTiers"
                          regex: 'Tier\\(name=(?<name>[^,]+), symbol=(?<symbol>[^,]+),'
                          values: [name, symbol]
                      - name: width
                        type: NUMBER
                """);
        Catalogue.Match m = c.find("pricing.updateTier");
        String answer = "Tier(name=gold, symbol=EUR-USD, width=2.0)\nTier(name=gold, symbol=EUR-USD, width=2.0)\n"
                + "header line\nTier(name=silver, symbol=GBP-USD, width=4.0)";
        List<List<String>> choices = Catalogue.choices(m, m.entry().getArguments().get(0), cmd -> {
            assertEquals("pricing.listTiers", cmd);
            return answer;
        });
        assertEquals(List.of(List.of("gold", "EUR-USD"), List.of("silver", "GBP-USD")), choices, "skips non-matching lines and repeats");
        assertNull(Catalogue.validate(m, List.of("silver", "GBP-USD", "3.5"), a -> choices));
        assertThrows(IllegalArgumentException.class, () -> Catalogue.parse("""
                commands:
                  - command: a.b
                    arguments:
                      - name: x
                        type: CHOICE
                        choicesFrom: {command: a.c, regex: "(?<n>.*)", values: [m]}
                """), "values must name groups in the regex");
    }

    @Test
    void aCatalogueTypoFailsAtLoad() {
        assertThrows(Exception.class, () -> Catalogue.parse("commands:\n  - command: a.b\n    argumnets: []\n"), "unknown field");
        assertThrows(IllegalArgumentException.class, () -> Catalogue.parse("commands:\n  - command: a.b\n    arguments:\n      - name: x\n        type: enum\n"));
        assertThrows(IllegalArgumentException.class, () -> Catalogue.parse("commands:\n  - command: a.b\n    arguments:\n      - name: x\n        type: choice\n"));
    }

    @Test
    void outcomeRecordsAreAuditFormatAndRoll() throws Exception {
        Path file = dir.resolve("auditLog-scheduler.yaml");
        OutcomeLog log = new OutcomeLog(file, 600, 2);
        Outcome o = new Outcome();
        o.setItemId("i1");
        o.setTitle("Switch {source}: feed1, [VENUE1]\nsecond line");
        o.setCommand("strat.A.setPriceSource");
        o.setArguments(List.of("feed1", "VENUE1", "EURUSD"));
        o.setState(OutcomeState.RAN_OK);
        o.setDecidedBy("trader");
        o.setDecidedAt(1_000);
        log.append(o);
        String text = Files.readString(file);
        assertTrue(text.startsWith("---\n#"), text);
        assertTrue(text.contains("event: SchedulerOutcome"));
        assertTrue(text.contains("title: \"Switch {source}: feed1, [VENUE1] | second line\","), text);
        o.setDetail("{\"status\":\"ok\", \"note\":\"a \\\\ b\"} *star - dash | pipe & amp ! bang % @ #hash");
        log.append(o);
        List<Map<String, Object>> records = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
                .readerFor(Map.class).<Map<String, Object>>readValues(Files.readString(file)).readAll();
        assertEquals(2, records.size(), "every record parses");
        Map<?, ?> node = (Map<?, ?>) ((List<?>) ((Map<?, ?>) records.get(1).get("eventLogRecord")).get("nodeLogs")).get(0);
        Map<?, ?> fields = (Map<?, ?>) node.get("scheduler");
        assertEquals(o.getDetail(), fields.get("detail"), "output comes back exactly");
        assertEquals("feed1 VENUE1 EURUSD", fields.get("arguments"));
        for (int i = 0; i < 4; i++) log.append(o);
        assertTrue(Files.exists(dir.resolve("auditLog-scheduler-1.yaml")));
        assertTrue(Files.exists(dir.resolve("auditLog-scheduler-2.yaml")));
        assertTrue(!Files.exists(dir.resolve("auditLog-scheduler-3.yaml")), "keeps only maxFiles rolled files");
    }

    @Test
    void aTornHistoryLineIsSkippedNotFatal() throws Exception {
        SchedulerStore store = new SchedulerStore(dir, 10);
        store.load();
        Outcome o = new Outcome();
        o.setItemId("i1");
        o.setState(OutcomeState.DECLINED);
        store.appendHistory(o);
        Files.writeString(dir.resolve("history.jsonl"), "{\"itemId\":\"i2\",\"sta", java.nio.file.StandardOpenOption.APPEND);
        SchedulerStore reloaded = new SchedulerStore(dir, 10);
        reloaded.load();
        assertEquals(List.of("i1"), reloaded.history().stream().map(Outcome::getItemId).toList());
        assertEquals(1, reloaded.skippedHistoryLines());
    }

    @Test
    void aPathologicalRegexFailsInsteadOfHanging() throws Exception {
        Catalogue c = Catalogue.parse("commands:\n  - command: \"{s}.x\"\n    arguments:\n      - name: v\n        type: CHOICE\n        choicesFrom: {command: \"{s}.list\", regex: '(a+)+$'}\n");
        Catalogue.Match m = c.find("p.x");
        long start = System.nanoTime();
        assertThrows(IllegalStateException.class, () -> Catalogue.choices(m, m.entry().getArguments().get(0), cmd -> "a".repeat(100_000) + "!"));
        assertTrue(System.nanoTime() - start < 5_000_000_000L, "gave up within the budget");
        // backtracking that keeps revisiting the same few positions is caught too
        Catalogue c2 = Catalogue.parse("commands:\n  - command: \"{s}.x\"\n    arguments:\n      - name: v\n        type: CHOICE\n        choicesFrom: {command: \"{s}.list\", regex: '^x((a|aa)+)+$'}\n");
        Catalogue.Match m2 = c2.find("p.x");
        long start2 = System.nanoTime();
        assertThrows(IllegalStateException.class, () -> Catalogue.choices(m2, m2.entry().getArguments().get(0), cmd -> "x" + "a".repeat(40) + "!"));
        assertTrue(System.nanoTime() - start2 < 5_000_000_000L, "gave up within the budget");
    }

    @Test
    void unicodeIsAlwaysWritableAndParsable() throws Exception {
        Path file = dir.resolve("u.yaml");
        OutcomeLog log = new OutcomeLog(file, 1_000_000, 2);
        for (String detail : List.of("a".repeat(499) + "\uD83D\uDE00tail", "c1 \u0080 \u009f nel \u0085 bom \uFEFF", "lone \uD83D end", "\u2028 \uFFFE")) {
            Outcome o = new Outcome();
            o.setItemId("i");
            o.setState(OutcomeState.FAILED);
            o.setDetail(detail);
            log.append(o);
        }
        List<Map<String, Object>> records = new com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
                .readerFor(Map.class).<Map<String, Object>>readValues(Files.readString(file)).readAll();
        assertEquals(4, records.size());
    }

    @Test
    void aHistoryTailCutInsideACharacterIsRepaired() throws Exception {
        SchedulerStore store = new SchedulerStore(dir, 10);
        store.load();
        Outcome o = new Outcome();
        o.setItemId("i1");
        o.setState(OutcomeState.DECLINED);
        o.setDetail("caf\u00e9");
        store.appendHistory(o);
        byte[] partial = "{\"itemId\":\"i2\",\"detail\":\"caf\u00e9".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(dir.resolve("history.jsonl"), java.util.Arrays.copyOf(partial, partial.length - 1), java.nio.file.StandardOpenOption.APPEND);
        SchedulerStore reloaded = new SchedulerStore(dir, 10);
        reloaded.load();
        assertEquals(1, reloaded.history().size());
        Outcome next = new Outcome();
        next.setItemId("i3");
        next.setState(OutcomeState.DECLINED);
        reloaded.appendHistory(next);
        SchedulerStore again = new SchedulerStore(dir, 10);
        again.load();
        assertEquals(List.of("i1", "i3"), again.history().stream().map(Outcome::getItemId).toList(), "the next append is not lost");
        assertEquals(0, again.skippedHistoryLines());
    }

    @Test
    void aRecordThatLandedBeforeItsWriteFailedIsFoundWhereItWasWrittenAcrossRolls() throws Exception {
        Path file = dir.resolve("roll.yaml");
        boolean[] failAfterWrite = {true};
        OutcomeLog log = new OutcomeLog(file, 900, 3) {
            @Override
            protected void writeRecord(byte[] bytes) throws java.io.IOException {
                super.writeRecord(bytes);
                if (failAfterWrite[0]) {
                    failAfterWrite[0] = false;
                    throw new java.io.IOException("flush reported failure after the bytes landed");
                }
            }
        };
        assertThrows(java.io.UncheckedIOException.class, () -> log.append(outcome("uncertain")));
        assertTrue(log.contains("uncertain"));
        int rolled = 0;
        for (int i = 0; rolled < 2; i++) { // two rolls: the record is now in -2
            boolean before = Files.exists(dir.resolve("roll-1.yaml"));
            log.append(outcome("other" + i));
            if (!before && Files.exists(dir.resolve("roll-1.yaml"))) rolled++;
            else if (before && Files.readString(dir.resolve("roll-1.yaml")).contains("itemId: \"other" + (i - 1))) rolled = Math.max(rolled, 2);
        }
        assertTrue(log.contains("uncertain"), "found in the rolled file it was written to");
        for (int i = 0; i < 40; i++) log.append(outcome("more" + i)); // rolled out of retention
        assertFalse(log.contains("uncertain"), "outside retention: not found, as documented");
    }

    @Test
    void anItemScannedAndAbsentIsNotScannedAgainUntilItIsWritten() throws Exception {
        Path file = dir.resolve("cache.yaml");
        OutcomeLog log = new OutcomeLog(file, 1_000_000, 3);
        log.append(outcome("present"));
        assertFalse(log.contains("absent"));
        new OutcomeLog(file, 1_000_000, 3).append(outcome("absent")); // written behind its back
        assertFalse(log.contains("absent"), "answered from the cache, not by another full scan");
        log.append(outcome("absent"));
        assertTrue(log.contains("absent"), "an attempt clears the cache and is looked for where it was written");
        log.forget("absent");
        assertTrue(log.contains("absent"), "after forget, a full scan finds it");
    }

    private static Outcome outcome(String itemId) {
        Outcome o = new Outcome();
        o.setItemId(itemId);
        o.setTitle("t");
        o.setState(OutcomeState.RAN_OK);
        return o;
    }

    @Test
    void historyIsCapped() {
        SchedulerStore store = new SchedulerStore(dir, 3);
        store.load();
        for (int i = 0; i < 5; i++) {
            Outcome o = new Outcome();
            o.setItemId("i" + i);
            o.setState(OutcomeState.DECLINED);
            store.appendHistory(o);
        }
        SchedulerStore reloaded = new SchedulerStore(dir, 3);
        reloaded.load();
        assertEquals(List.of("i2", "i3", "i4"), reloaded.history().stream().map(Outcome::getItemId).toList());
    }
}
