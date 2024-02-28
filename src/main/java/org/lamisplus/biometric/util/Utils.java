package org.lamisplus.biometric.util;

import com.neurotec.plugins.NDataFileManager;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.lamisplus.biometric.config.Application;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Utils {
    public static final String FILE_SEPARATOR = System.getProperty("file.separator");
    public static final String PATH_SEPARATOR = System.getProperty("path.separator");
    public static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private static ImageIcon createImageIcon(String path) {
        URL imgURL = Utils.class.getClassLoader().getResource(path);
        if (imgURL == null) {
            System.err.println("Couldn't find file: " + path);
            return null;
        }
        return new ImageIcon(imgURL);
    }

    public static void writeText(String pathname, String text) throws IOException {
        if (text == null) throw new NullPointerException("text");
        File file = new File(pathname);
        if ((file.isAbsolute()) && (file.getParentFile() != null))
            file.getParentFile().mkdirs();
        else if ((!(file.exists())) || (!(file.isFile()))) {
            throw new IllegalArgumentException("No such file: " + file.getAbsolutePath());
        }
        Writer writer = new FileWriter(file);
        Closeable resource = writer;
        try {
            BufferedWriter bw = new BufferedWriter(writer);
            resource = bw;
            bw.write(text);
        } finally {
            resource.close();
        }
    }

    public static String readText(String file) throws IOException {
        StringBuilder sb;
        Reader reader = new FileReader(file);
        Closeable resource = reader;
        try {
            String str1;
            BufferedReader br = new BufferedReader(reader);
            resource = br;
            sb = new StringBuilder();
            String line = br.readLine();
            if (line == null) {
                str1 = "";
                return str1;
            }

            sb.append(line);
            line = br.readLine();
            if (line == null) {
                str1 = sb.toString();
                return str1;
            }

        } finally {
            resource.close();
        }
        return sb.toString();
    }

    public static String getWorkingDirectory() {
        return (Application.biometricDirectory != null && !Application.biometricDirectory.isEmpty())
                ? Application.biometricDirectory : System.getProperty("user.dir");
    }

    public static String getHomeDirectory() {
        return System.getProperty("user.home");
    }

    public static String combinePath(String part1, String part2) {
        return String.format("%s%s%s", part1, FILE_SEPARATOR, part2);
    }

    public static Icon createIcon(String path) {
        return createImageIcon(path);
    }

    public static Image createIconImage(String path) {
        ImageIcon icon = createImageIcon(path);
        if (icon == null) {
            return null;
        }
        return icon.getImage();
    }

    public static boolean isNullOrEmpty(String value) {
        return ((value == null) || ("".equals(value)));
    }

    public static int qualityToPercent(int value) {
        return ((2 * value * 100 + 255) / 510);
    }

    public static int qualityFromPercent(int value) {
        return ((2 * value * 255 + 100) / 200);
    }

    public static String matchingThresholdToString(int value) {
        double p = -value / 12.0D;
        NumberFormat nf = NumberFormat.getPercentInstance();
        nf.setMaximumFractionDigits(Math.max(0, (int) Math.ceil(-p) - 2));
        nf.setMinimumIntegerDigits(1);
        return nf.format(Math.pow(10.0D, p));
    }

    public static int matchingThresholdFromString(String value) throws ParseException {
        char percent = new DecimalFormatSymbols().getPercent();
        value = value.replace(percent, ' ');
        Number number = NumberFormat.getNumberInstance().parse(value);
        double parse = number.doubleValue();
        double p = Math.log10(Math.max(4.9E-324D, Math.min(1.0D, parse / 100.0D)));
        return Math.max(0, (int) Math.round(-12.0D * p));
    }

    public static void initDataFiles(Object obj)
            throws Exception {
        InputStream is;
        if (obj == null) throw new NullPointerException("obj");
        String outputFolder = combinePath(System.getProperty("java.io.tmpdir"), "data");

        URL srcLocation = obj.getClass().getProtectionDomain().getCodeSource().getLocation();
        ZipInputStream zip = new ZipInputStream(srcLocation.openStream());
        boolean isZip = false;
        try {
            while (true) {
                ZipEntry e = zip.getNextEntry();
                if (e == null) {
                    break;
                }
                isZip = true;
                String name = e.getName();
                if (name.endsWith(".ndf")) {
                    is = obj.getClass().getClassLoader().getResourceAsStream(name);
                    FileUtils.copyInputStreamToFile(is, new File(outputFolder, FilenameUtils.getName(name)));
                }
            }
        } finally {
            zip.close();
        }

        if (!(isZip)) {
            URL resourceUrl = Utils.class.getClassLoader().getResource("data");
            if (resourceUrl != null) {
                List<String> files = IOUtils.readLines(Objects.requireNonNull(Utils.class.getClassLoader().getResourceAsStream("data")), Charsets.UTF_8);
                for (String file : files) {
                    InputStream is1 = Utils.class.getClassLoader().getResourceAsStream(combinePath("data", file));
                    FileUtils.copyInputStreamToFile(is1, new File(outputFolder, file));
                }
            } else {
                throw new IllegalStateException("Data directory is not present inside the jar file");
            }
        }

        NDataFileManager.getInstance().addFromDirectory(outputFolder, true);
    }

    public static final class ImageFileFilter extends FileFilter {
        private final List<String> extensions;
        private final String description;

        public ImageFileFilter(String extentionsString) {
            this(extentionsString, null);
        }

        public ImageFileFilter(String extentionsString, String description) {
            this.extensions = new ArrayList();
            StringTokenizer tokenizer = new StringTokenizer(extentionsString, ";");
            StringBuilder sb;
            if (description == null)
                sb = new StringBuilder(64);
            else {
                sb = new StringBuilder(description).append(" (");
            }
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                sb.append(token);
                sb.append(", ");
                this.extensions.add(token.replaceAll("\\*", "").replaceAll("\\.", ""));
            }
            sb.delete(sb.length() - 2, sb.length());
            if (description != null) {
                sb.append(')');
            }
            this.description = sb.toString();
        }

        public boolean accept(File f) {
            for (String extension : this.extensions) {
                if ((f.isDirectory()) || (f.getName().toLowerCase().endsWith(extension.toLowerCase()))) {
                    return true;
                }
            }
            return false;
        }

        public String getDescription() {
            return this.description;
        }

        public List<String> getExtensions() {
            return new ArrayList(this.extensions);
        }
    }

    public static final class XMLFileFilter extends FileFilter {
        private final String description;

        public XMLFileFilter() {
            this.description = "*.xml";
        }

        public XMLFileFilter(String description) {
            if (description == null)
                this.description = "*.xml";
            else
                this.description = description + " (*.xml)";
        }

        public boolean accept(File f) {
            return ((f.isDirectory()) || (f.getName().endsWith(".xml")));
        }

        public String getDescription() {
            return this.description;
        }
    }

    public static final class TemplateFileFilter extends FileFilter {
        private final String description;

        public TemplateFileFilter() {
            this.description = "*.dat; *.data";
        }

        public TemplateFileFilter(String description) {
            if (description == null)
                this.description = "*.dat; *.data";
            else
                this.description = description + " (*.dat; *.data)";
        }

        public boolean accept(File f) {
            return ((f.isDirectory()) || (f.getName().endsWith(".dat")) || (f.getName().endsWith(".data")));
        }

        public String getDescription() {
            return this.description;
        }
    }
}
