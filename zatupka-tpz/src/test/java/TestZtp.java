import io.rsug.zatupka.tpz.CatRec;
import io.rsug.zatupka.tpz.Ztp;
import io.rsug.zatupka.tpz.ZtpFile;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;

public class TestZtp {
    @Test
    public void main() throws Exception {
        for (final Path p : Files.newDirectoryStream(Paths.get("C:\\workspace\\tpz_svalka\\tpz"), "*tpz")) {
            System.out.println(p);
            final InputStream is = Files.newInputStream(p);
            final List<ZtpFile> files = ZtpFile.parseZip(is);
            is.close();
            for (final ZtpFile f : files) {
                System.out.println(f.fileName);
                for (final CatRec cr : f.records) {
                    String s = MessageFormat.format("[{0}]_{1}.{2} length {3} bytes", cr.idx, cr.type, cr.isXml ? "xml" : "bin", cr.lengthData);
                    System.out.print(s);
                    if (cr.isXml && cr.lengthData > 0) {
                        f.validateXml(cr);
                        System.out.println("\t validated OK");
                    } else {
                        System.out.println();
                    }
                }
            }
        }
    }

    @Test
    public void unpage() throws Exception {
        boolean saveExtracted = false;
        OutputStream os;
        for (final Path p : Files.newDirectoryStream(Paths.get("C:\\workspace\\tpz_svalka\\esr"), "*.??t")) {
            final int sz = (int) Files.size(p);
            final InputStream is = Files.newInputStream(p);
            final Ztp ztp = new Ztp();
            ztp.unpage(is, sz);
            final byte[] unpagedBytes = ztp.getBytes();
            assert unpagedBytes.length <= sz;
            is.close();

            System.out.println(MessageFormat.format("File {0} source length={1,number}, unpacked={2,number}", p, sz, unpagedBytes.length));
            if (saveExtracted) {
                os = Files.newOutputStream(p.resolveSibling(p + ".unpaged"));
                os.write(unpagedBytes);
                os.close();
            }

            final List<CatRec> records = ztp.parse();
            if (saveExtracted) {
                os = Files.newOutputStream(p.resolveSibling(p + ".catalog"));
                os.write(unpagedBytes, ztp.catalogBeginLength()[0], ztp.catalogBeginLength()[1]);
                os.close();
            }
            for (final CatRec cr : records) {
                String fn = MessageFormat.format("{0}_{1}_{2}.{3}", p, cr.idx, cr.type, cr.isXml ? "xml" : "bin");
                if (saveExtracted) {
                    os = Files.newOutputStream(Paths.get(fn));
                    os.write(unpagedBytes, cr.beginData, cr.lengthData);
                    os.close();
                }
                if (cr.isXml && cr.lengthData > 0) {
                    ztp.validateXml(cr);
                }
            }
        }
    }

    @Test
    public void header() {

    }
}
