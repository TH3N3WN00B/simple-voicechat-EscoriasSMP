package de.maxhenkel.voicechat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VersionTest {

    @Test
    void testParseFullVersion() {
        Version v = Version.fromVersionString("1.2.3");
        assertNotNull(v);
        assertEquals(1, v.major);
        assertEquals(2, v.minor);
        assertEquals(3, v.patch);
    }

    @Test
    void testParseShortVersions() {
        Version v1 = Version.fromVersionString("1");
        assertNotNull(v1);
        assertEquals(1, v1.major);
        assertEquals(0, v1.minor);
        assertEquals(0, v1.patch);

        Version v12 = Version.fromVersionString("1.2");
        assertNotNull(v12);
        assertEquals(1, v12.major);
        assertEquals(2, v12.minor);
        assertEquals(0, v12.patch);
    }

    @Test
    void testParseInvalidVersions() {
        assertNull(Version.fromVersionString(""));
        assertNull(Version.fromVersionString("abc"));
        assertNull(Version.fromVersionString("1.2.3.4"));
        assertNull(Version.fromVersionString("1.2-beta"));
        assertNull(Version.fromVersionString("1.x"));
    }

    @Test
    void testOpenALVersionParsing() {
        Version v = Version.fromOpenALVersion("OpenAL Soft 1.20.1 ALSOFT 1.23.1");
        assertNotNull(v);
        assertEquals(1, v.major);
        assertEquals(23, v.minor);
        assertEquals(1, v.patch);
    }

    @Test
    void testCompareTo() {
        Version one = new Version(1, 0, 0);
        Version oneDotOne = new Version(1, 1, 0);
        Version oneDotOneDotOne = new Version(1, 1, 1);
        Version two = new Version(2, 0, 0);

        assertTrue(one.compareTo(oneDotOne) < 0);
        assertTrue(oneDotOne.compareTo(oneDotOneDotOne) < 0);
        assertTrue(oneDotOneDotOne.compareTo(two) < 0);
        assertTrue(two.compareTo(one) > 0);
        assertEquals(0, oneDotOne.compareTo(new Version(1, 1, 0)));
    }

    @Test
    void testToString() {
        assertEquals("1.2.3", new Version(1, 2, 3).toString());
        assertEquals("1.0.0", new Version(1, 0, 0).toString());
    }
}