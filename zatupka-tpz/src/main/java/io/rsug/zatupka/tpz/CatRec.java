package io.rsug.zatupka.tpz;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.text.MessageFormat;

public class CatRec {
    /**
     * Кусок содержимого из TPT/DLT
     */
    public final String type;
    public final int idx, offset, length;
    public int beginData, lengthData;
    public final boolean isXml, isBin;

    CatRec(int index, String t, String o, String l) {
        idx = index;
        type = t;
        offset = Integer.parseInt(o);
        length = Integer.parseInt(l);
        isXml = t.equals("header") || t.equals("metaModel-PVC") || t.equals("modelGeneralData") || t.equals("modelElement") || t.equals("metaModel-Model");
        isBin = t.equals("modelVersionSet") || t.equals("pvcVersionSet") || t.equals("pvcVersionSetSpecialization") || t.equals("knownDevlines") || t.equals("pvcElement");
        assert isXml || isBin : "Unknown type " + t;
    }

    void feed(ByteBuffer bb) throws ZtpException {
        long tmp;
        int hx = this.offset;
        if (type.equals("header")) {
            long blockSize = bb.getLong(hx);
            tmp = bb.getLong(hx + 8);
            beginData = hx + 16;
            lengthData = length - 16;
            hx += length;
        } else if (isXml) {
            tmp = bb.getLong(hx);
            beginData = hx + 8;
            lengthData = length - 8;
            hx += length;
            assert lengthData == 0 ||
                    bb.getLong(beginData) == 0x3c3f786d6c207665L : "Parsing error at type=" + type + ",index=" + idx + ",offset=" + offset;
            tmp = bb.getLong(hx - 8);
            assert lengthData == 0 ||
                    (type.equals("metaModel-PVC") && tmp == 0x6368656d613e0d0aL) ||
                    (type.equals("metaModel-Model") && tmp == 0x6f6e58694f626a3eL) ||
                    (type.equals("modelGeneralData") && tmp == 0x657461446174613eL) ||
                    (type.equals("modelElement") && tmp == 0x313a78694f626a3eL);
            for (int t = beginData; t < lengthData + beginData; t++) {
                final byte b = bb.get(t);
                if (b == 0x00) {
                    String s = new MessageFormat("Found invalid byte={0} at 0x{1} on XML part. Entry type={2}, index={3}").format(new Object[]{b, Integer.toHexString(t), this.type, this.idx});
                    throw new ZtpException(s);
                }
            }

        } else if (isBin) {
            beginData = hx + 8;
            lengthData = length - 8;
            hx += 8 + length;
        }
    }

    public void validateXml(byte[] b) throws XMLStreamException, ZtpException {
        assert isXml;
        if (lengthData == 0) {
            throw new ZtpException("Nothing to validate, empty block");
        }
        final XMLInputFactory xmlif = XMLInputFactory.newInstance();
        final XMLStreamReader xr = xmlif.createXMLStreamReader(new ByteArrayInputStream(b, this.beginData, this.lengthData));
        while (xr.hasNext()) {
            xr.next();
        }
        xr.close();
    }

}
