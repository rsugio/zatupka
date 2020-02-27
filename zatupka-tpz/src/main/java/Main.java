import io.rsug.zatupka.tpz.CatRec;
import io.rsug.zatupka.tpz.Ztp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Main {
    public static void main(String[] args) throws Exception {
        Package m = Main.class.getPackage();

        System.out.println(MessageFormat.format("{0} {2} by {1}", Main.class.getPackage().getImplementationTitle(), Main.class.getPackage().getImplementationVendor(), Main.class.getPackage().getImplementationVersion()));
        if (args.length == 0) {
            System.out.println(" Использование: список tpz-файлов, ожидайте создания парных zip-файлов");
            System.exit(0);
        }
        for (String arg : args) {
            final Path p = Paths.get(arg);
            if (!p.getFileName().toString().endsWith(".tpz")) {
                System.err.println(MessageFormat.format("Файл {0} должен называться *.tpz, пропущен", p));
            } else if (Files.isRegularFile(p)) {
                System.err.println(MessageFormat.format("Обработка файла {0} начата", p));
                ZipInputStream zis = new ZipInputStream(Files.newInputStream(p));
                ZipEntry ze = zis.getNextEntry();
                while (ze != null) {
                    if (!ze.isDirectory() && (ze.getName().endsWith(".tpt") || ze.getName().endsWith(".dlt"))) {

                        final Path rez = p.resolveSibling(ze.getName() + ".zip");
                        Files.deleteIfExists(rez);
                        Files.createFile(rez);
                        final ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(rez));

                        final Ztp ztp = new Ztp();
                        ztp.unpage(zis, (int) Files.size(p));
                        final List<CatRec> crl = ztp.parse();
                        for (final CatRec cr : crl) {
                            if (cr.isXml && cr.lengthData > 0) {
                                cr.validateXml(ztp.getBytes());
                            }
                            ZipEntry zo = new ZipEntry(MessageFormat.format("{1}_{0}.{2}", cr.type, cr.idx, cr.isXml ? "xml" : "bin"));
                            zos.putNextEntry(zo);
                            zos.write(ztp.getBytes(), cr.beginData, cr.lengthData);
                            zos.flush();
                            zos.closeEntry();
                        }
                        zos.close();
                        System.err.println(MessageFormat.format("Создан файл {0}", rez));
                    }
                    ze = zis.getNextEntry();
                }
            } else {
                System.err.println(MessageFormat.format("Файл {0} отсутствует", p));
            }


        }
    }
}
