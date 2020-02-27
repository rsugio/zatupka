import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Первая, неудачная попытка -- через XML
 */
public class Ideas {
    class CatRec {
        final String type;
        final int idx, offset, length;
        final boolean isXml;

        CatRec(int index, String t, String o, String l) {
            idx = index;
            type = t;
            offset = Integer.parseInt(o);
            length = Integer.parseInt(l);
            isXml = t.equals("header") || t.equals("modelGeneralData") || t.equals("modelElement");
        }
    }

    private List<CatRec> parseCatalogXml(byte[] array, int len) throws XMLStreamException {
        XMLInputFactory xmlif = XMLInputFactory.newInstance();
        XMLStreamReader xr = xmlif.createXMLStreamReader(new ByteArrayInputStream(array, 0, len));
        List<CatRec> lst = new ArrayList<>(1024);
        int idx = 0;
        while (xr.hasNext()) {
            int ix = xr.next();
            if (ix == XMLStreamConstants.START_ELEMENT && xr.getLocalName().equals("segment")) {
                String type = xr.getAttributeValue("", "type");
                String offset = xr.getAttributeValue("", "offset");
                String length = xr.getAttributeValue("", "length");
                lst.add(new CatRec(idx, type, offset, length));
                idx++;
            }
        }
        return lst;
    }

    private int findXmlPreamble(byte[] src, int limit, byte[] dst, int x3) {
        boolean xmlStarted = false;
        int zc = 0, cx = 0;

        while (x3 < limit) {
            byte by = src[x3];
            if (zc == 4) {
                boolean nextXml = src[x3 + 4] == 0x3c && src[x3 + 5] == 0x3f && src[x3 + 6] == 0x78 && src[x3 + 7] == 0x6d;
                if (nextXml && !xmlStarted) {
                    cx = 0;
                    xmlStarted = true;
                } else if (xmlStarted) {
                    cx -= 4;
                }
                zc = 0;
                x3 += 4;
                by = src[x3];
//                assert (by != 0) || (b[x3 + 1] != 0) || (b[x3 + 2] != 0) || (b[x3 + 3] != 0);
            } else if (by == 0) {
                zc++;
            } else {
                zc = 0;
            }
            dst[cx] = by;
            cx++;
            x3++;
        }
        return cx;
    }

    public void version3(byte[] b, int sz, Path tmp) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(b);
        int x = bb.getInt(sz - 4);
        byte[] cat = new byte[sz - x];
        int cx = findXmlPreamble(b, sz - 9, cat, x);

        OutputStream os;
        if (tmp != null) {
            os = Files.newOutputStream(Paths.get(tmp.toString() + ".xml"));
            os.write(cat, 0, cx);
            os.close();
        }
        List<CatRec> lst = parseCatalogXml(cat, cx);
        assert lst.size() > 0;
        cat = null;
        for (final CatRec cr : lst) {
            if (cr.isXml) {
                byte[] bt = new byte[cr.length * 2];
                cx = findXmlPreamble(b, cr.offset + cr.length * 2, bt, cr.offset + cr.length);
                if (tmp != null) {
                    os = Files.newOutputStream(Paths.get(tmp.toString() + cr.idx + "_" + cr.type + ".xml"));
                    os.write(bt, 0, cx);
                    os.close();
                }
            }
        }
    }

    public void version2(byte[] b, int sz, Path tmp) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(b);
        int x = bb.getInt(sz - 4);
        int x3 = x, zc = 0, cx = 0;
        byte[] cat = new byte[sz - x];

        boolean xmlStarted = false;
        while (x3 < sz - 8) {
            byte by = b[x3];
            if (zc == 4) {
                boolean nextXml = b[x3 + 4] == 0x3c && b[x3 + 5] == 0x3f && b[x3 + 6] == 0x78 && b[x3 + 7] == 0x6d;
                if (nextXml && !xmlStarted) {
                    cx = 0;
                    xmlStarted = true;
                } else if (xmlStarted) {
                    cx -= 4;
                }
                zc = 0;
                x3 += 4;
                by = b[x3];
//                assert (by != 0) || (b[x3 + 1] != 0) || (b[x3 + 2] != 0) || (b[x3 + 3] != 0);
            } else if (by == 0) {
                zc++;
            } else {
                zc = 0;
            }
            cat[cx] = by;
            cx++;
            x3++;
        }
        OutputStream os;
        if (tmp != null) {
            os = Files.newOutputStream(Paths.get(tmp.toString() + ".xml"));
            os.write(cat, 0, cx);
            os.close();
        }
        List<CatRec> lst = parseCatalogXml(cat, cx);
        assert lst.size() > 0;
        cat = null;

//        int hx = 38;
//        while (hx < sz) {
//            int t = bb.getInt(hx);
//            int u = bb.getInt(hx + 4);
//            System.out.println("[" + hx + "]==" + t + ", [" + (hx + 4) + "]==" + u);
//            hx = hx + 8 + u;
//        }
        for (final CatRec rec : lst) {

//            System.out.println("offset="+rec.offset + " 0x"+Integer.toHexString(rec.offset) + " len="+rec.length + " 0x"+Integer.toHexString(rec.length));
//            byte[] xml = new byte[rec.length];
//            int y = rec.offset + 16, ly = 0;
//            while (ly < rec.length - 8) {
//                xml[ly] = b[y];
//                ly++;
//                y++;
//            }
//            os = Files.newOutputStream(Paths.get(tmp.toString() + rec.idx + "_" + rec.type + ".xml"));
//            os.write(xml, 0, ly);
//            os.close();
        }
    }

    private static void __(String t, long val) {
        System.out.println("0x" + Long.toHexString(val) + "\tdec=" + val + "\t" + t);
    }

    public void arifmetika(final byte[] src, final int len, final Path p) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(src);
        int MAX_CHUNK_SIZE = 10 * 1024 * 1024;
        __("source length", len);
        int trailer = (int) bb.getLong(len - 8);
        __("trailer to catalog", trailer);
        int catbegin = trailer + 8;
        long checksum = bb.getLong((int) trailer);
        __("catalog CRC32", checksum);
        int catlen = len - trailer - 8 - 8;
        __("catalog length", catlen);
        CRC32 c32 = new CRC32(), c32noh = new CRC32();
        c32.update(src, catbegin, catlen);
        __("calculated crc32 full", c32.getValue());
        c32noh.update(src, catbegin + 39, catlen - 39);
        __("calculated crc32 data", c32noh.getValue());


    }

    private static long extractLong(RandomAccessFile file, long pos) throws IOException {
        file.seek(pos);
        return file.readLong();
    }

    //@Test этот тест уже не работает, оставлено просто так
    public void simple_tpt() throws Exception {
        for (final Path p : Files.newDirectoryStream(Paths.get("C:\\workspace\\tpz_svalka\\esr"), "*.tpt")) {
            if (true) {
                final long sz = Files.size(p);
                final InputStream is = Files.newInputStream(p);
                System.out.println("" + p.getFileName() + ": " + sz);
                final byte[] b = new byte[(int) sz];
                int x = is.read(b);
                assert x == sz;
                is.close();
                arifmetika(b, (int) sz, p);
                version3(b, (int) sz, p);
            }
        }
    }

    //@Test этот тест уже не работает, оставлено просто так
    public void xi_content() throws Exception {
        for (final Path p : Files.newDirectoryStream(Paths.get("C:\\workspace\\_xicontent\\"), "*.ZIP")) {
            System.out.println(p);
            final ZipInputStream zis = new ZipInputStream(Files.newInputStream(p));
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                if (!ze.isDirectory() && ze.getName().endsWith(".tpz")) {
                    System.out.println("\t" + ze.getName());
                    final ZipInputStream zis2 = new ZipInputStream(zis);
                    ZipEntry ze2 = zis2.getNextEntry();
                    while (ze2 != null) {
                        if (!ze2.isDirectory() && ze2.getName().endsWith(".tpt")) {
                            byte[] ar = zis2.readAllBytes();
                            Path pt = null; // Paths.get("c:\\workspace\\_xicontent2\\"+ze.getName());
                            System.out.println("\t\t" + ze2.getName() + " " + ar.length + " => " + pt);
                            version3(ar, ar.length, pt);
                        }
                        zis2.closeEntry();
                        ze2 = zis2.getNextEntry();
                    }
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            zis.close();
        }
    }
}
