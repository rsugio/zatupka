package io.rsug.zatupka.tpz;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Ztp {
    /**
     * Разбор распакованного транспортного файла
     *
     * @author iliya.kuznetsov@gmail.com
     * @version 0.0.2
     */
    private byte[] unpaged = null;
    final List<CatRec> records = new ArrayList<>(128);
    private int catalogBeginReal, catalogBeginXml, catalogLengthXml;

    public List<CatRec> parse() throws XMLStreamException, ZtpException {
        assert unpaged != null;
        assert unpaged.length > 20000 : "Too little buffer to parse: " + unpaged.length + " bytes";
        ByteBuffer bb = ByteBuffer.wrap(unpaged);

        catalogBeginReal = (int) bb.getLong(unpaged.length - 8);
        catalogBeginXml = catalogBeginReal + 8;
        assert bb.getLong(catalogBeginXml) == 0x3c3f786d6c207665L;
        catalogLengthXml = unpaged.length - catalogBeginReal - 16;

        long lt = bb.getLong(catalogBeginReal);
        assert lt <= 0xFFFFFFFFL : "crc32=0x" + Long.toHexString(lt) + " at pos=0x" + Long.toHexString(catalogBeginReal);
        final int catalogSize = unpaged.length - catalogBeginReal - 16;
        for (int i = catalogBeginXml; i < unpaged.length - 8; i++) {
            if (unpaged[i] == 0) {
                throw new ZtpException("0x00 found in xml catalog at 0x" + Integer.toHexString(i) + " (decimal=" + i + ")");
            }
        }
        final XMLInputFactory xmlif = XMLInputFactory.newInstance();
        final XMLStreamReader xr = xmlif.createXMLStreamReader(new ByteArrayInputStream(unpaged, catalogBeginReal + 8, catalogSize));
        assert records.size() == 0;
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

        for (final CatRec cr : records) {
            cr.feed(bb);
        }
        return records;
    }

    /**
     * Возвращает начало и длину каталога
     *
     * @return
     */
    public int[] catalogBeginLength() {
        assert catalogBeginXml > 0;
        assert catalogLengthXml > 0;
        int[] tmp = {catalogBeginXml, catalogLengthXml};
        return tmp;
    }

    public void unpage(InputStream is, int possibleSize) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream(possibleSize);
        final int pageSize = 0x100000;
        int actualRead = pageSize, crcRead = 0;
        byte[] pageBytes = new byte[pageSize], eight = new byte[8];
//        final byte[] etalon = new byte[]{0, 0, 0, 0};

        while (actualRead == pageSize) {
            boolean z8 = eight[0] == 0 && eight[1] == 0 && eight[2] == 0 && eight[3] == 0;
//            boolean z = Arrays.mismatch(etalon, eight) > 3;
//            assert z == z8;

            int x = 1;
            actualRead = is.read(pageBytes);
            while (actualRead < pageSize && x > 0) {
                x = is.read(pageBytes, actualRead, pageSize - actualRead);
                if (x > 0) {
                    actualRead += x;
                }
            }
            if (crcRead == 8 && !z8) {
                bos.write(eight, 0, crcRead);
                bos.write(pageBytes, 8, actualRead - 8);
            } else {
                bos.write(pageBytes, 0, actualRead);
            }
            crcRead = is.read(eight);
        }
        bos.close();
        unpaged = bos.toByteArray();
    }

    public byte[] getBytes() {
        assert unpaged != null;
        return unpaged;
    }

    public void validateXml(CatRec cr) throws XMLStreamException, ZtpException {
        cr.validateXml(getBytes());
    }
}
