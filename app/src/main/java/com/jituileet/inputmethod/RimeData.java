package com.jituileet.inputmethod;

import android.content.Context;

import java.io.*;

/** Copies the bundled Rime configuration into a writable filesystem tree. */
public final class RimeData {

    private RimeData() {}

    public static File sharedDir(Context c) {
        return new File(c.getFilesDir(), "rime");
    }

    public static File userDir(Context c) {
        return new File(c.getFilesDir(), "rime-user");
    }

    public static void ensure(Context c) throws IOException {

        File shared = sharedDir(c);
        File user = userDir(c);

        if (!shared.exists() ||
                !new File(shared, ".installed-1.0.0-rime-tv-fix3").exists()) {

            delete(shared);

            copyAssetTree(c, "rime", shared);

            new File(shared, ".installed-1.0.0-rime-tv-fix3").createNewFile();
        }

        if (!user.exists()) {
            user.mkdirs();
        }
    }

    /**
     * Select dictionary from built-in or uploaded dictionary.
     */
    public static void selectDictionary(Context c, String fileName)
            throws IOException {

        ensure(c);

        // Built-in dictionary
        if (fileName == null ||
                fileName.equals("内置词库") || fileName.equals("输入法自带词库") || fileName.equals("Built-in dictionary")) {

            File patch =
                    new File(
                            sharedDir(c),
                            "luna_pinyin_simp.custom.yaml"
                    );

            try (FileOutputStream fos =
                         new FileOutputStream(patch)) {

                fos.write(
                        (
                                "patch:\n" +
                                "  engine/filters:\n" +
                                "    - uniquifier\n"
                        ).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
            }

            RimeNative.deploy();

            return;
        }

        // Uploaded dictionary
        File f =
                new File(
                        c.getFilesDir(),
                        "dicts/" + fileName
                );

        if (!f.exists()) {
            return;
        }

        byte[] data;

        try (
                FileInputStream in =
                        new FileInputStream(f);

                ByteArrayOutputStream out =
                        new ByteArrayOutputStream()
        ) {

            byte[] b = new byte[8192];

            int n;

            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }

            data = out.toByteArray();
        }

        installDictionary(c, fileName, data);
    }

    /**
     * Install uploaded dictionary.
     */
    public static void installDictionary(
            Context c,
            String fileName,
            byte[] data
    ) throws IOException {

        ensure(c);

        File out =
                new File(
                        sharedDir(c),
                        fileName
                );

        try (FileOutputStream fos =
                     new FileOutputStream(out)) {

            fos.write(data);
        }

        String dictionary =
                fileName.endsWith(".dict.yaml")
                        ? fileName.substring(
                                0,
                                fileName.length() - 9
                        )
                        : fileName;

        try {

            String text =
                    new String(
                            data,
                            java.nio.charset.StandardCharsets.UTF_8
                    );

            java.util.regex.Matcher m =
                    java.util.regex.Pattern
                            .compile(
                                    "(?m)^name:\\s*([^\\s#]+)\\s*$"
                            )
                            .matcher(text);

            if (m.find()) {

                dictionary =
                        m.group(1).trim();
            }

        } catch (Exception ignored) {

        }

        String patch =
                "patch:\n" +
                "  engine/filters:\n" +
                "    - uniquifier\n" +
                "  translator/dictionary: "
                + dictionary +
                "\n";

        try (FileOutputStream fos =
                     new FileOutputStream(
                             new File(
                                     sharedDir(c),
                                     "luna_pinyin_simp.custom.yaml"
                             )
                     )) {

            fos.write(
                    patch.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                    )
            );
        }

        RimeNative.deploy();
    }

    private static void copyAssetTree(
            Context c,
            String path,
            File out
    ) throws IOException {

        out.mkdirs();

        String[] children =
                c.getAssets().list(path);

        if (children == null ||
                children.length == 0) {

            try (
                    InputStream in =
                            c.getAssets().open(path);

                    FileOutputStream fos =
                            new FileOutputStream(out)
            ) {

                byte[] b = new byte[8192];

                int n;

                while ((n = in.read(b)) > 0) {

                    fos.write(b, 0, n);
                }
            }

            return;
        }

        for (String child : children) {

            copyAssetTree(
                    c,
                    path + "/" + child,
                    new File(out, child)
            );
        }
    }

    private static void delete(File f) {

        if (!f.exists()) {
            return;
        }

        if (f.isDirectory()) {

            File[] xs = f.listFiles();

            if (xs != null) {

                for (File x : xs) {

                    delete(x);
                }
            }
        }

        f.delete();
    }
}
