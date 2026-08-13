package cn.longer233.gamenarrator.mobile;

import org.junit.Test;
import java.io.StringReader;
import static org.junit.Assert.*;

public class CubeLutParserTest {
    @Test public void parsesIdentityCubeWithRedFastestOrdering() throws Exception {
        String identity = "TITLE \"identity\"\nLUT_3D_SIZE 2\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 1 1 1\n"
                + "0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1\n";
        CubeLutParser.Parsed parsed = CubeLutParser.parse(new StringReader(identity));
        assertEquals(2, parsed.size());
        assertEquals(0xffff0000, parsed.cube()[1][0][0]);
        assertEquals(0xff00ff00, parsed.cube()[0][1][0]);
        assertEquals(0xff0000ff, parsed.cube()[0][0][1]);
    }

    @Test public void rejectsWrongEntryCountAndOneDimensionalLut() {
        assertThrows(IllegalArgumentException.class, () -> CubeLutParser.parse(new StringReader("LUT_3D_SIZE 2\n0 0 0\n")));
        assertThrows(IllegalArgumentException.class, () -> CubeLutParser.parse(new StringReader("LUT_1D_SIZE 2\n0 0 0\n1 1 1\n")));
    }

    @Test public void clampsOutOfRangeColorValues() throws Exception {
        String cube = "LUT_3D_SIZE 2\n" + "-1 2 .5\n".repeat(8);
        assertEquals(0xff00ff80, CubeLutParser.parse(new StringReader(cube)).cube()[0][0][0]);
    }
}
