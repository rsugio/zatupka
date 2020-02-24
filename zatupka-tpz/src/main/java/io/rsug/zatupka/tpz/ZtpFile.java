package io.rsug.zatupka.tpz;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZtpFile {
    public final String fileName;
    final public List<CatRec> records;
    final public String extension;
    final private Ztp ztp;

    private ZtpFile(Ztp ztp, String unpackedFileName, List<CatRec> records) {
        this.ztp = ztp;
        this.fileName = unpackedFileName;
        final int x = unpackedFileName.lastIndexOf('.');
        if (x > 0) {
            this.extension = unpackedFileName.substring(x);
        } else {
            this.extension = null;
        }
        this.records = records;
    }

    public static List<ZtpFile> parseZip(InputStream is) throws IOException, ZtpException {
        final List<ZtpFile> files = new LinkedList<ZtpFile>();
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry ze = zis.getNextEntry();
        while (ze != null) {
            if (!ze.isDirectory() && (ze.getName().endsWith(".tpt") || ze.getName().endsWith(".dlt"))) {
                ZtpFile zf = parseZipEntry(zis, ze);
                files.add(zf);
            }
            zis.closeEntry();
            ze = zis.getNextEntry();
        }
        zis.close();
        return files;
    }

    public static ZtpFile parseZipEntry(ZipInputStream zis, ZipEntry ze) throws IOException, ZtpException {
        final Ztp ztp = new Ztp();
        int sizeUnpacked = (int) ze.getSize();
        ztp.unpage(zis, sizeUnpacked > 0 ? sizeUnpacked : 65536);
        try {
            ztp.parse();
            return new ZtpFile(ztp, ze.getName(), ztp.records);
        } catch (XMLStreamException xe) {
            throw new ZtpException(xe.getMessage());
        }
    }

    public void validateXml(CatRec cr) throws XMLStreamException, ZtpException {
        cr.validateXml(ztp.getBytes());
    }

}
