package com.yoordi.rank.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventDtoTest {

    @Test
    void eventDto_shouldCreateInstance() {
        EventDto.Props props = new EventDto.Props("view");
        EventDto event = new EventDto("evt-001", "user-123", "webtoon-456", System.currentTimeMillis(), props);

        assertNotNull(event);
        assertEquals("evt-001", event.eventId());
        assertEquals("user-123", event.userId());
        assertEquals("webtoon-456", event.contentId());
        assertTrue(event.ts() > 0);
        assertEquals("view", event.props().action());
    }

    @Test
    void props_shouldCreateInstance() {
        EventDto.Props props = new EventDto.Props("like");

        assertNotNull(props);
        assertEquals("like", props.action());
    }

    @Test
    void eventDto_withNullValues_shouldWork() {
        EventDto event = new EventDto(null, null, null, 0, null);

        assertNotNull(event);
        assertNull(event.eventId());
        assertNull(event.userId());
        assertNull(event.contentId());
        assertEquals(0, event.ts());
        assertNull(event.props());
    }

    @Test
    void eventDto_equality_shouldWork() {
        EventDto.Props props = new EventDto.Props("view");
        EventDto event1 = new EventDto("evt-001", "user-123", "webtoon-456", 12345L, props);
        EventDto event2 = new EventDto("evt-001", "user-123", "webtoon-456", 12345L, props);

        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }
}
