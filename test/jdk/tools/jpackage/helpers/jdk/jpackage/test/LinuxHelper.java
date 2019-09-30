/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.jpackage.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LinuxHelper {
    private static String getRelease(JPackageCommand cmd) {
        return cmd.getArgumentValue("--linux-app-release", () -> "1");
    }

    public static String getPackageName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);
        return cmd.getArgumentValue("--linux-package-name",
                () -> cmd.name().toLowerCase());
    }

    static String getBundleName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final PackageType packageType = cmd.packageType();
        String format = null;
        switch (packageType) {
            case LINUX_DEB:
                format = "%s_%s-%s_%s";
                break;

            case LINUX_RPM:
                format = "%s-%s-%s.%s";
                break;
        }

        final String release = getRelease(cmd);
        final String version = cmd.version();

        return String.format(format,
                getPackageName(cmd), version, release, getPackageArch(packageType))
                + packageType.getSuffix();
    }

    public static Stream<Path> getPackageFiles(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final PackageType packageType = cmd.packageType();
        final Path packageFile = cmd.outputBundle();

        Executor exec = new Executor();
        switch (packageType) {
            case LINUX_DEB:
                exec.setExecutable("dpkg")
                        .addArgument("--contents")
                        .addArgument(packageFile);
                break;

            case LINUX_RPM:
                exec.setExecutable("rpm")
                        .addArgument("-qpl")
                        .addArgument(packageFile);
                break;
        }

        Stream<String> lines = exec.executeAndGetOutput().stream();
        if (packageType == PackageType.LINUX_DEB) {
            // Typical text lines produced by dpkg look like:
            // drwxr-xr-x root/root         0 2019-08-30 05:30 ./opt/appcategorytest/runtime/lib/
            // -rw-r--r-- root/root    574912 2019-08-30 05:30 ./opt/appcategorytest/runtime/lib/libmlib_image.so
            // Need to skip all fields but absolute path to file.
            lines = lines.map(line -> line.substring(line.indexOf(" ./") + 2));
        }
        return lines.map(Path::of);
    }

    public static List<String> getPrerequisitePackages(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);
        var packageType = cmd.packageType();
        switch (packageType) {
            case LINUX_DEB:
                return Stream.of(getDebBundleProperty(cmd.outputBundle(),
                        "Depends").split(",")).map(String::strip).collect(
                        Collectors.toList());

            case LINUX_RPM:
                return new Executor().setExecutable("rpm")
                .addArguments("-qp", "-R", cmd.outputBundle().toString())
                .executeAndGetOutput();
        }
        // Unreachable
        return null;
    }

    public static String getBundleProperty(JPackageCommand cmd,
            String propertyName) {
        return getBundleProperty(cmd,
                Map.of(PackageType.LINUX_DEB, propertyName,
                        PackageType.LINUX_RPM, propertyName));
    }

    public static String getBundleProperty(JPackageCommand cmd,
            Map<PackageType, String> propertyName) {
        cmd.verifyIsOfType(PackageType.LINUX);
        var packageType = cmd.packageType();
        switch (packageType) {
            case LINUX_DEB:
                return getDebBundleProperty(cmd.outputBundle(), propertyName.get(
                        packageType));

            case LINUX_RPM:
                return getRpmBundleProperty(cmd.outputBundle(), propertyName.get(
                        packageType));
        }
        // Unrechable
        return null;
    }

    static Path getLauncherPath(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final String launcherName = cmd.name();
        final String launcherRelativePath = Path.of("/bin", launcherName).toString();

        return getPackageFiles(cmd).filter(path -> path.toString().endsWith(
                launcherRelativePath)).findFirst().or(() -> {
            TKit.assertUnexpected(String.format(
                    "Failed to find %s in %s package", launcherName,
                    getPackageName(cmd)));
            return null;
        }).get();
    }

    static long getInstalledPackageSizeKB(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final Path packageFile = cmd.outputBundle();
        switch (cmd.packageType()) {
            case LINUX_DEB:
                return Long.parseLong(getDebBundleProperty(packageFile,
                        "Installed-Size"));

            case LINUX_RPM:
                return Long.parseLong(getRpmBundleProperty(packageFile, "Size")) >> 10;
        }

        return 0;
    }

    static String getDebBundleProperty(Path bundle, String fieldName) {
        return new Executor()
                .setExecutable("dpkg-deb")
                .addArguments("-f", bundle.toString(), fieldName)
                .executeAndGetFirstLineOfOutput();
    }

    static String getRpmBundleProperty(Path bundle, String fieldName) {
        return new Executor()
                .setExecutable("rpm")
                .addArguments(
                        "-qp",
                        "--queryformat",
                        String.format("%%{%s}", fieldName),
                        bundle.toString())
                .executeAndGetFirstLineOfOutput();
    }

    static void verifyPackageBundleEssential(JPackageCommand cmd) {
        String packageName = LinuxHelper.getPackageName(cmd);
        TKit.assertNotEquals(0L, LinuxHelper.getInstalledPackageSizeKB(
                cmd), String.format(
                        "Check installed size of [%s] package in KB is not zero",
                        packageName));

        final boolean checkPrerequisites;
        if (cmd.isRuntime()) {
            Path runtimeDir = cmd.appRuntimeInstallationDirectory();
            Set<Path> expectedCriticalRuntimePaths = CRITICAL_RUNTIME_FILES.stream().map(
                    runtimeDir::resolve).collect(Collectors.toSet());
            Set<Path> actualCriticalRuntimePaths = getPackageFiles(cmd).filter(
                    expectedCriticalRuntimePaths::contains).collect(
                            Collectors.toSet());
            checkPrerequisites = expectedCriticalRuntimePaths.equals(
                    actualCriticalRuntimePaths);
        } else {
            checkPrerequisites = true;
        }

        List<String> prerequisites = LinuxHelper.getPrerequisitePackages(cmd);
        if (checkPrerequisites) {
            final String vitalPackage = "libc";
            TKit.assertTrue(prerequisites.stream().filter(
                    dep -> dep.contains(vitalPackage)).findAny().isPresent(),
                    String.format(
                            "Check [%s] package is in the list of required packages %s of [%s] package",
                            vitalPackage, prerequisites, packageName));
        } else {
            TKit.trace(String.format(
                    "Not cheking %s required packages of [%s] package",
                    prerequisites, packageName));
        }
    }

    static void addBundleDesktopIntegrationVerifier(PackageTest test,
            boolean integrated) {
        final String xdgUtils = "xdg-utils";

        test.addBundleVerifier(cmd -> {
            List<String> prerequisites = getPrerequisitePackages(cmd);
            boolean xdgUtilsFound = prerequisites.contains(xdgUtils);
            if (integrated) {
                TKit.assertTrue(xdgUtilsFound, String.format(
                        "Check [%s] is in the list of required packages %s",
                        xdgUtils, prerequisites));
            } else {
                TKit.assertFalse(xdgUtilsFound, String.format(
                        "Check [%s] is NOT in the list of required packages %s",
                        xdgUtils, prerequisites));
            }
        });

        test.forTypes(PackageType.LINUX_DEB, () -> {
            addDebBundleDesktopIntegrationVerifier(test, integrated);
        });
    }

    private static void addDebBundleDesktopIntegrationVerifier(PackageTest test,
            boolean integrated) {
        Function<List<String>, String> verifier = (lines) -> {
            // Lookup for xdg commands
            return lines.stream().filter(line -> {
                Set<String> words = Set.of(line.split("\\s+"));
                return words.contains("xdg-desktop-menu") || words.contains(
                        "xdg-mime") || words.contains("xdg-icon-resource");
            }).findFirst().orElse(null);
        };

        test.addBundleVerifier(cmd -> {
            TKit.withTempDirectory("dpkg-control-files", tempDir -> {
                // Extract control Debian package files into temporary directory
                new Executor()
                .setExecutable("dpkg")
                .addArguments(
                        "-e",
                        cmd.outputBundle().toString(),
                        tempDir.toString()
                ).execute().assertExitCodeIsZero();

                Path controlFile = Path.of("postinst");

                // Lookup for xdg commands in postinstall script
                String lineWithXsdCommand = verifier.apply(
                        Files.readAllLines(tempDir.resolve(controlFile)));
                String assertMsg = String.format(
                        "Check if %s@%s control file uses xdg commands",
                        cmd.outputBundle(), controlFile);
                if (integrated) {
                    TKit.assertNotNull(lineWithXsdCommand, assertMsg);
                } else {
                    TKit.assertNull(lineWithXsdCommand, assertMsg);
                }
            });
        });
    }

    static void initFileAssociationsTestFile(Path testFile) {
        try {
            // Write something in test file.
            // On Ubuntu and Oracle Linux empty files are considered
            // plain text. Seems like a system bug.
            //
            // $ >foo.jptest1
            // $ xdg-mime query filetype foo.jptest1
            // text/plain
            // $ echo > foo.jptest1
            // $ xdg-mime query filetype foo.jptest1
            // application/x-jpackage-jptest1
            //
            Files.write(testFile, Arrays.asList(""));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Path getSystemDesktopFilesFolder() {
        return Stream.of("/usr/share/applications",
                "/usr/local/share/applications").map(Path::of).filter(dir -> {
            return Files.exists(dir.resolve("defaults.list"));
        }).findFirst().orElseThrow(() -> new RuntimeException(
                "Failed to locate system .desktop files folder"));
    }

    static void addFileAssociationsVerifier(PackageTest test, FileAssociations fa) {
        test.addInstallVerifier(cmd -> {
            PackageTest.withTestFileAssociationsFile(fa, testFile -> {
                String mimeType = queryFileMimeType(testFile);

                TKit.assertEquals(fa.getMime(), mimeType, String.format(
                        "Check mime type of [%s] file", testFile));

                String desktopFileName = queryMimeTypeDefaultHandler(mimeType);

                Path desktopFile = getSystemDesktopFilesFolder().resolve(
                        desktopFileName);

                TKit.assertFileExists(desktopFile);

                TKit.trace(String.format("Reading [%s] file...", desktopFile));
                String mimeHandler = Files.readAllLines(desktopFile).stream().peek(
                        v -> TKit.trace(v)).filter(
                                v -> v.startsWith("Exec=")).map(
                                v -> v.split("=", 2)[1]).findFirst().orElseThrow();

                TKit.trace(String.format("Done"));

                TKit.assertEquals(cmd.launcherInstallationPath().toString(),
                        mimeHandler, String.format(
                                "Check mime type handler is the main application launcher"));

            });
        });

        test.addUninstallVerifier(cmd -> {
            PackageTest.withTestFileAssociationsFile(fa, testFile -> {
                String mimeType = queryFileMimeType(testFile);

                TKit.assertNotEquals(fa.getMime(), mimeType, String.format(
                        "Check mime type of [%s] file", testFile));

                String desktopFileName = queryMimeTypeDefaultHandler(fa.getMime());

                TKit.assertNull(desktopFileName, String.format(
                        "Check there is no default handler for [%s] mime type",
                        fa.getMime()));
            });
        });
    }

    private static String queryFileMimeType(Path file) {
        return new Executor()
                .setExecutable("xdg-mime")
                .addArguments("query", "filetype", file.toString())
                .executeAndGetFirstLineOfOutput();
    }

    private static String queryMimeTypeDefaultHandler(String mimeType) {
        return new Executor()
                .setExecutable("xdg-mime")
                .addArguments("query", "default", mimeType)
                .executeAndGetFirstLineOfOutput();
    }

    private static String getPackageArch(PackageType type) {
        if (archs == null) {
            archs = new HashMap<>();
        }

        String arch = archs.get(type);
        if (arch == null) {
            Executor exec = new Executor();
            switch (type) {
                case LINUX_DEB:
                    exec.setExecutable("dpkg").addArgument(
                            "--print-architecture");
                    break;

                case LINUX_RPM:
                    exec.setExecutable("rpmbuild").addArgument(
                            "--eval=%{_target_cpu}");
                    break;
            }
            arch = exec.executeAndGetFirstLineOfOutput();
            archs.put(type, arch);
        }
        return arch;
    }

    static final Set<Path> CRITICAL_RUNTIME_FILES = Set.of(Path.of(
            "lib/server/libjvm.so"));

    static private Map<PackageType, String> archs;
}
