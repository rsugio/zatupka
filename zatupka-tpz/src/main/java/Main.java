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

        String s = MessageFormat.format("{0} {2} by {1}", new Object[]{Main.class.getPackage().getImplementationTitle(), Main.class.getPackage().getImplementationVendor(), Main.class.getPackage().getImplementationVersion()});
        System.out.println(s);
        if (args.length == 0) {
            System.out.println("Использование: список tpz-файлов, ожидайте создания парных zip-файлов");
            System.exit(-1);
        }
        for (int i = 0; i < args.length; i++) {
            final Path p = Paths.get(args[i]);
            if (!p.getFileName().toString().endsWith(".tpz")) {
                System.err.println(MessageFormat.format("Файл {0} должен называться *.tpz, пропущен", new Object[]{p}));
            } else if (Files.isRegularFile(p)) {
                System.err.println(MessageFormat.format("Обработка файла {0} начата", new Object[]{p}));
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
                            ZipEntry zo = new ZipEntry(MessageFormat.format("{1}_{0}.{2}", new Object[]{cr.type, cr.idx, cr.isXml ? "xml" : "bin"}));
                            zos.putNextEntry(zo);
                            zos.write(ztp.getBytes(), cr.beginData, cr.lengthData);
                            zos.flush();
                            zos.closeEntry();
                        }
                        zos.close();
                        System.err.println(MessageFormat.format("Создан файл {0}", new Object[]{rez}));
                    }
                    ze = zis.getNextEntry();
                }
            } else {
                System.err.println(MessageFormat.format("Файл {0} отсутствует", new Object[]{p}));
            }


        }
    }
}
