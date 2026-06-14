package com.seetalk.id;

import com.seetalk.model.id.SnowflakeIdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdWorkerTest {

    @BeforeEach
    void setUp() {
        new SnowflakeIdWorker(1, 1);
    }

    @Test
    void generatesUniqueIncreasingIds() {
        long first = SnowflakeIdWorker.nextId();
        long second = SnowflakeIdWorker.nextId();

        assertTrue(second > first);

        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(SnowflakeIdWorker.nextId());
        }
        assertEquals(1000, ids.size());
    }
}
