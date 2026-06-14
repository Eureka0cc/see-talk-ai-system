package com.seetalk.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistorySearchSupportToolsTest {

    @Test
    void resolvesAbstractRecallForWhoQuestions() {
        assertEquals(HistorySearchSupportTools.SearchMode.ABSTRACT_RECALL,
                HistorySearchSupportTools.resolveMode("我说过我喜欢谁"));
        assertEquals(HistorySearchSupportTools.SearchMode.ABSTRACT_RECALL,
                HistorySearchSupportTools.resolveMode("我喜欢谁"));
    }

    @Test
    void resolvesKeywordForConcreteNames() {
        assertEquals(HistorySearchSupportTools.SearchMode.KEYWORD,
                HistorySearchSupportTools.resolveMode("黎伟伟"));
        assertEquals(HistorySearchSupportTools.SearchMode.KEYWORD,
                HistorySearchSupportTools.resolveMode("我喜欢黎伟伟"));
    }

    @Test
    void extractsTokensForKeywordFallback() {
        List<String> tokens = HistorySearchSupportTools.extractSearchTokens("我喜欢黎伟伟");
        assertTrue(tokens.contains("黎伟伟"));
        assertFalse(tokens.contains("谁"));
    }

    @Test
    void matchesContentByAnyToken() {
        List<String> tokens = HistorySearchSupportTools.extractSearchTokens("我喜欢黎伟伟");
        assertTrue(HistorySearchSupportTools.matchesAnyToken("我喜欢黎伟伟", tokens));
        assertFalse(HistorySearchSupportTools.matchesAnyToken("今天天气不错", tokens));
    }
}
