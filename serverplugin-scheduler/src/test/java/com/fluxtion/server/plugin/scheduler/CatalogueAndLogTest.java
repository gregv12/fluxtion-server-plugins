package com.fluxtion.server.plugin.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(text.contains("title: Switch source feed1 VENUE1 | second line,"), text);
        assertTrue(text.contains("arguments: feed1 VENUE1 EURUSD,"), text);
        for (int i = 0; i < 6; i++) log.append(o);
        assertTrue(Files.exists(dir.resolve("auditLog-scheduler-1.yaml")));
        assertTrue(Files.exists(dir.resolve("auditLog-scheduler-2.yaml")));
        assertTrue(!Files.exists(dir.resolve("auditLog-scheduler-3.yaml")), "keeps only maxFiles rolled files");
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
