import org.junit.Test;

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


/**
 * Тестовый класс проверки идей
 */
public class Ideas3 {
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

    private final List<CatRec> records = new ArrayList<>(128);

    void l(InputStream is) throws IOException, XMLStreamException {
        boolean storeTmp = true;
        Path pathCatalogTmp = null;
        OutputStream os = null;

        byte[] by = is.readAllBytes();
        final int sz = by.length;
        assert sz > 1024;
        ByteBuffer bb = ByteBuffer.wrap(by);

        final long catalogBeginVirtual = bb.getLong(sz - 8);
        assert catalogBeginVirtual <= 0xFFFFFFFFL : "invalid catalogBegin: 0x" + Long.toHexString(catalogBeginVirtual);
        final long _catalog = bb.getLong(sz - 16);
        assert _catalog == 0x6174616c6f673e0aL;

        int pos = (int) catalogBeginVirtual;
        while (pos < sz - 16 && bb.getLong(pos + 8) != 0x3c3f786d6c207665L) {
            pos++;
        }
        final int catalogBeginReal = pos;
        final long zrc32 = bb.getLong(catalogBeginReal);
        assert zrc32 <= 0xFFFFFFFFL : "crc32=0x" + Long.toHexString(zrc32) + " at pos=0x" + Long.toHexString(catalogBeginReal);
        int catalogSize = sz - catalogBeginReal - 16;
        final XMLInputFactory xmlif = XMLInputFactory.newInstance();
        XMLStreamReader xr;

        if (storeTmp) {
            pathCatalogTmp = Files.createTempFile("catalog3__", ".xml");
            os = Files.newOutputStream(pathCatalogTmp);
        }

        byte[] bt;
        if (catalogSize < 0x100000) {
            xr = xmlif.createXMLStreamReader(new ByteArrayInputStream(by, catalogBeginReal + 8, catalogSize));
            if (storeTmp) {
                os.write(by, catalogBeginReal + 8, catalogSize);
                os.close();
            }
        } else {
            bt = new byte[catalogSize];
            int cx = 0, bx = catalogBeginReal + 8;
//            System.out.println("RealCatalogCRC at 0x"+Integer.toHexString(catalogBeginReal) + ", block from 0x"+Integer.toHexString(catalogBeginReal+8));
            //            System.out.println("Virtual catalog CRC at 0x"+Long.toHexString(catalogBeginVirtual));
            while (bx < sz - 8) {
                if (bb.getInt(bx) == 0) {
                    bx += 8;
                } else {
                    bt[cx++] = by[bx];
                    bx++;
                }
            }
            xr = xmlif.createXMLStreamReader(new ByteArrayInputStream(bt, 0, cx));
            if (storeTmp) {
                os.write(bt, 0, cx);
                os.close();
            }
        }
        records.clear();
        int catXmlIndex = 0;
        while (xr.hasNext()) {
            final int t = xr.next();
            if (t == XMLStreamConstants.START_ELEMENT && xr.getLocalName().equals("segment")) {
                String type = xr.getAttributeValue("", "type");
                String offset = xr.getAttributeValue("", "offset");
                String length = xr.getAttributeValue("", "length");
                records.add(new CatRec(catXmlIndex++, type, offset, length));
            }
        }
        bt = null;
        xr.close();
        assert records.size() == catXmlIndex;

        int ws = 0x100008, currentPage = ws;
        int hx = -1;
        long blockSize;
        for (int cx = 0; cx < records.size(); ) {
            final CatRec cr = records.get(cx);
            os = Files.newOutputStream(Files.createTempFile("_" + cx + "_" + cr.type + "_", ".bin"));
            if (cx == 0) {
                hx = cr.offset;
                assert cr.type.equals("header");
                blockSize = bb.getLong(hx);
                assert blockSize == cr.length - 8;
                hx += 8;
                long crc32 = bb.getLong(hx);
                assert crc32 < 0xFFFFFFFFL;
                hx += 8;
                os.write(by, hx, cr.length - 16);
                hx += cr.length - 16;
            } else if (cr.type.startsWith("metaModel-")) {
                blockSize = bb.getLong(hx);
                assert blockSize == cr.length - 8;
                hx += 8;
                os.write(by, hx, cr.length - 8);
                hx += cr.length - 8;
            } else if (cr.type.equals("modelGeneralData")) {
                long crc32 = bb.getLong(hx);
                assert crc32 < 0xFFFFFFFFL;
                hx += 8;
                os.write(by, hx, cr.length - 8);
                hx += cr.length - 8;
            } else if (cr.type.equals("pvcElement") || cr.type.equals("modelElement")) {
                long crc32 = bb.getLong(hx);
                int hxhist = hx;

                if (hx + 8 + cr.length > currentPage) {
                    hx += 8;
                    if (true) {
                        int len1 = currentPage - hx - 8, len2 = cr.length - len1 - 8;
                        os.write(by, hx, len1);
                        hx = currentPage;
                        os.write(by, hx, len2);
                        hx += len2;
                    }
                    currentPage += ws;
                } else {
                    hx += 8;
                    os.write(by, hx, cr.length - 8);
                    hx += cr.length - 8;
                }
                os.flush();
                if (cr.type.equals("modelElement")) {
                    assert bb.getLong(hxhist + 8) == 0x3c3f786d6c207665L;
                    assert bb.getLong(hx - 8) == 0x313a78694f626a3eL;
                }
            } else if (cr.type.equals("pvcVersionSet") || cr.type.equals("pvcVersionSetSpecialization") || cr.type.equals("modelVersionSet") || cr.type.equals("knownDevlines")) {
                os.write(by, hx, cr.length);
                hx += cr.length;
            } else {
                assert false : cr.type;
            }
            os.close();
            cx++;
        }
        assert hx == catalogBeginReal;


    }

    void r(byte[] by) throws IOException, XMLStreamException {
        boolean storeCatalog = true, storeEntries = true;
        Path pathCatalogTmp = null;
        OutputStream os = null;

        final int sz = by.length;
        assert sz > 20000 : "suspicious size, too small";
        ByteBuffer bb = ByteBuffer.wrap(by);

        final int catalogBeginReal = (int) bb.getLong(sz - 8);
        assert bb.getLong(catalogBeginReal + 8) == 0x3c3f786d6c207665L;

        final long zrc32 = bb.getLong(catalogBeginReal);
        assert zrc32 <= 0xFFFFFFFFL : "crc32=0x" + Long.toHexString(zrc32) + " at pos=0x" + Long.toHexString(catalogBeginReal);
        final int catalogSize = sz - catalogBeginReal - 16;
        for (int i = catalogBeginReal + 8; i < sz - 8; i++) {
            if (by[i] == 0)
                throw new IOException("0x00 found in xml catalog at 0x" + Integer.toHexString(i) + " (decimal=" + i + ")");
        }

        final XMLInputFactory xmlif = XMLInputFactory.newInstance();
        XMLStreamReader xr;

        if (storeCatalog) {
            pathCatalogTmp = Files.createTempFile("catalog3__", ".xml");
            os = Files.newOutputStream(pathCatalogTmp);
            os.write(by, catalogBeginReal + 8, catalogSize);
            os.close();
        }

        xr = xmlif.createXMLStreamReader(new ByteArrayInputStream(by, catalogBeginReal + 8, catalogSize));
        records.clear();
        int catXmlIndex = 0;
        while (xr.hasNext()) {
            final int t = xr.next();
            if (t == XMLStreamConstants.START_ELEMENT && xr.getLocalName().equals("segment")) {
                String type = xr.getAttributeValue("", "type");
                String offset = xr.getAttributeValue("", "offset");
                String length = xr.getAttributeValue("", "length");
                records.add(new CatRec(catXmlIndex++, type, offset, length));
            }
        }
        xr.close();
        assert records.size() == catXmlIndex;

//        if (true) return;
        byte[] bt;

        int hx = -1;
        long blockSize;
        for (int cx = 0; cx < records.size(); ) {
            final CatRec cr = records.get(cx);
            if (storeEntries) os = Files.newOutputStream(Files.createTempFile("_" + cx + "_" + cr.type + "_", ".bin"));
            if (cx == 0) {
                hx = cr.offset;
                assert cr.type.equals("header");
                blockSize = bb.getLong(hx);
                assert blockSize == cr.length - 8;
                hx += 8;
                long crc32 = bb.getLong(hx);
                assert crc32 < 0xFFFFFFFFL;
                hx += 8;
                if (storeEntries) os.write(by, hx, cr.length - 16);
                hx += cr.length - 16;
            } else if (cr.type.startsWith("metaModel-")) {
                blockSize = bb.getLong(hx);
                assert blockSize == cr.length - 8;
                hx += 8;
                if (storeEntries) os.write(by, hx, cr.length - 8);
                hx += cr.length - 8;
            } else if (cr.type.equals("modelGeneralData")) {
                long crc32 = bb.getLong(hx);
                assert crc32 < 0xFFFFFFFFL;
                hx += 8;
                if (storeEntries) os.write(by, hx, cr.length - 8);
                hx += cr.length - 8;
            } else if (cr.type.equals("pvcElement") || cr.type.equals("modelElement")) {
                long crc32 = bb.getLong(hx);
                int hxhist = hx;

                hx += 8;
                if (storeEntries) os.write(by, hx, cr.length - 8);
                hx += cr.length - 8;
                if (storeEntries) os.flush();
                if (cr.type.equals("modelElement")) {
                    assert bb.getLong(hxhist + 8) == 0x3c3f786d6c207665L;
                    assert bb.getLong(hx - 8) == 0x313a78694f626a3eL;
                }
            } else if (cr.type.equals("pvcVersionSet") || cr.type.equals("pvcVersionSetSpecialization") || cr.type.equals("modelVersionSet") || cr.type.equals("knownDevlines")) {
                if (storeEntries) os.write(by, hx, cr.length);
                hx += cr.length;
            } else {
                assert false : cr.type;
            }
            if (storeEntries) os.close();
            cx++;
        }
        assert hx == catalogBeginReal;
    }

    @Test
    public void testTpt() throws Exception {
        for (final Path p : Files.newDirectoryStream(Paths.get("C:\\workspace\\tpz_svalka\\esr"), "*.tpt")) {
            final int sz = (int) Files.size(p);
            final InputStream is = Files.newInputStream(p);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream(sz);
            int ps = 0x100008, cx = ps, page = 0, offset = 0;
            byte[] tmp = new byte[ps];
            ByteBuffer bb = ByteBuffer.wrap(tmp);
            while (cx == ps) {
                cx = is.read(tmp);
                long first = bb.getLong(0);
                long last = bb.getLong(cx - 8);
                System.out.println("Page " + page + " read +0x" + Integer.toHexString(cx) + ", offset=0x" + Integer.toHexString(offset) + ",first=0x" + Long.toHexString(first) + ",last=0x" + Long.toHexString(last));
                offset += cx;

                if (cx == ps) {
                    bos.write(tmp, 0, cx - 8);
                } else {
                    bos.write(tmp, 0, cx);
                }
                page++;
            }
            is.close();
            bos.close();
            tmp = bos.toByteArray();
            OutputStream os = Files.newOutputStream(Paths.get(p + ".unpacked"));
            os.write(tmp);
            os.close();
            System.out.println("\n\t" + p.getFileName() + ": source size=" + sz + ", unpacked size=" + tmp.length + ", diff=" + (sz - tmp.length));
            r(tmp);
        }
    }


    @Test
    public void testTpt2() throws Exception {
        for (final Path p : Files.newDirectoryStream(Paths.get("C:\\workspace\\tpz_svalka\\dir"), "*.tpt")) {
            final int sz = (int) Files.size(p);
            final InputStream is = Files.newInputStream(p);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream(sz);
            int ps = 0x100000, cx = ps, k8 = 0, page = 0;
            byte[] tmp = new byte[ps], c8 = new byte[8];

            while (cx == ps) {
                boolean z8 = c8[0] == 0 && c8[1] == 0 && c8[2] == 0 && c8[3] == 0;
                cx = is.read(tmp);
//                System.out.println("page="+page+", read="+cx+", z8="+z8);
                if (cx == ps) {
                    if (k8 == 8 && !z8) {
                        bos.write(c8, 0, k8);
                        bos.write(tmp, 8, cx - 8);
                    } else {
                        bos.write(tmp, 0, cx);
                    }
                } else {
                    if (k8 > 0) {
                        bos.write(c8, 0, k8);
                        bos.write(tmp, 8, cx - 8);
                    } else {
                        bos.write(tmp, 0, cx);
                    }
                }
                k8 = is.read(c8);
                page++;
            }
            is.close();
            bos.close();
            tmp = bos.toByteArray();
            OutputStream os = Files.newOutputStream(Paths.get(p + ".2unpacked"));
            os.write(tmp);
            os.close();
            System.out.println("\n\t" + p.getFileName() + ": source size=" + sz + ", unpacked size=" + tmp.length + ", diff=" + (sz - tmp.length));
            r(tmp);
        }
    }
}
