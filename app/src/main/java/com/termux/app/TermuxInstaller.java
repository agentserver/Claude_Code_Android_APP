package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.system.Os;
import android.util.Pair;
import android.view.WindowManager;

import com.termux.R;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.errors.Error;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.data.DataUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
import static com.termux.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH;

/**
 * Install the Termux bootstrap packages if necessary by following the below steps:
 * <p/>
 * (1) If $PREFIX already exist, assume that it is correct and be done. Note that this relies on that we do not create a
 * broken $PREFIX directory below.
 * <p/>
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 * <p/>
 * (3) A staging directory, $STAGING_PREFIX, is cleared if left over from broken installation below.
 * <p/>
 * (4) The zip file is loaded from a shared library.
 * <p/>
 * (5) The zip, containing entries relative to the $PREFIX, is is downloaded and extracted by a zip input stream
 * continuously encountering zip file entries:
 * <p/>
 * (5.1) If the zip entry encountered is SYMLINKS.txt, go through it and remember all symlinks to setup.
 * <p/>
 * (5.2) For every other zip entry, extract it into $STAGING_PREFIX and set execute permissions if necessary.
 */
final class TermuxInstaller {

    private static final String LOG_TAG = "TermuxInstaller";

    /** Performs bootstrap setup if necessary. */
    static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        String bootstrapErrorMessage;
        Error filesDirectoryAccessibleError;

        // This will also call Context.getFilesDir(), which should ensure that termux files directory
        // is created if it does not already exist
        filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true);
        boolean isFilesDirectoryAccessible = filesDirectoryAccessibleError == null;

        // Termux can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !PackageUtils.isCurrentUserThePrimaryUser(activity)) {
            bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_not_primary_user_message,
                MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            Logger.logError(LOG_TAG, "isFilesDirectoryAccessible: " + isFilesDirectoryAccessible);
            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.exitAppWithErrorMessage(activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage);
            return;
        }

        if (!isFilesDirectoryAccessible) {
            bootstrapErrorMessage = Error.getMinimalErrorString(filesDirectoryAccessibleError);
            //noinspection SdCardPath
            if (PackageUtils.isAppInstalledOnExternalStorage(activity) &&
                !TermuxConstants.TERMUX_FILES_DIR_PATH.equals(activity.getFilesDir().getAbsolutePath().replaceAll("^/data/user/0/", "/data/data/"))) {
                bootstrapErrorMessage += "\n\n" + activity.getString(R.string.bootstrap_error_installed_on_portable_sd,
                    MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            }

            Logger.logError(LOG_TAG, bootstrapErrorMessage);
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            MessageDialogUtils.showMessage(activity,
                activity.getString(R.string.bootstrap_error_title),
                bootstrapErrorMessage, null);
            return;
        }

        // If prefix directory exists, even if its a symlink to a valid directory and symlink is not broken/dangling
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)) {
            if (TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
                Logger.logInfo(LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" exists but is empty or only contains specific unimportant files.");
            } else if (!isBootstrapFilesValid()) {
                Logger.logError(LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" is missing required bootstrap files. Reinstalling bootstrap.");
                Error error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                if (error != null) {
                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                    return;
                }
            } else {
                Error compatError = ensureBootstrapCompatPrefixSymlink();
                if (compatError != null) {
                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(compatError));
                    return;
                }
                Error aptError = ensureAptCacheConfig();
                if (aptError != null) {
                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(aptError));
                    return;
                }
                Error permsError = ensureBootstrapFilePermissions();
                if (permsError != null) {
                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(permsError));
                    return;
                }
                String abiMismatch = getBootstrapAbiMismatchMessage();
                if (abiMismatch != null) {
                    showBootstrapErrorDialog(activity, whenDone, abiMismatch);
                    return;
                }
                whenDone.run();
                return;
            }
        } else if (FileUtils.fileExists(TERMUX_PREFIX_DIR_PATH, false)) {
            Logger.logInfo(LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" does not exist but another file exists at its destination.");
        }

        final ProgressDialog progress = ProgressDialog.show(activity, null, activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread() {
            @Override
            public void run() {
                try {
                    Logger.logInfo(LOG_TAG, "Installing " + TermuxConstants.TERMUX_APP_NAME + " bootstrap packages.");

                    Error error;

                    // Delete prefix staging directory or any file at its destination
                    error = FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Delete prefix directory or any file at its destination
                    error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Create prefix staging directory if it does not already exist and set required permissions
                    error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Create prefix directory if it does not already exist and set required permissions
                    error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    Logger.logInfo(LOG_TAG, "Extracting bootstrap zip to prefix staging directory \"" + TERMUX_STAGING_PREFIX_DIR_PATH + "\".");

                    final byte[] buffer = new byte[8096];
                    final List<Pair<String, String>> symlinks = new ArrayList<>(50);

                    final byte[] zipBytes = loadZipBytes();
                    try (ZipInputStream zipInput = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                        ZipEntry zipEntry;
                        while ((zipEntry = zipInput.getNextEntry()) != null) {
                            if (zipEntry.getName().equals("SYMLINKS.txt")) {
                                BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                                String line;
                                while ((line = symlinksReader.readLine()) != null) {
                                    String[] parts = line.split("←");
                                    if (parts.length != 2)
                                        throw new RuntimeException("Malformed symlink line: " + line);
                                    String oldPath = parts[0];
                                    String newPath = TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1];
                                    symlinks.add(Pair.create(oldPath, newPath));

                                    error = ensureDirectoryExists(new File(newPath).getParentFile());
                                    if (error != null) {
                                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                                        return;
                                    }
                                }
                            } else {
                                String zipEntryName = zipEntry.getName();
                                File targetFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName);
                                boolean isDirectory = zipEntry.isDirectory();

                                error = ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());
                                if (error != null) {
                                    showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                                    return;
                                }

                                if (!isDirectory) {
                                    try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                        int readBytes;
                                        while ((readBytes = zipInput.read(buffer)) != -1)
                                            outStream.write(buffer, 0, readBytes);
                                    }
                                    if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("libexec") ||
                                        zipEntryName.startsWith("lib/apt/apt-helper") || zipEntryName.startsWith("lib/apt/methods")) {
                                        //noinspection OctalInteger
                                        Os.chmod(targetFile.getAbsolutePath(), 0700);
                                    }
                                }
                            }
                        }
                    }

                    if (symlinks.isEmpty())
                        throw new RuntimeException("No SYMLINKS.txt encountered");
                    for (Pair<String, String> symlink : symlinks) {
                        Os.symlink(symlink.first, symlink.second);
                    }

                    Logger.logInfo(LOG_TAG, "Moving termux prefix staging to prefix directory.");

                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        throw new RuntimeException("Moving termux prefix staging to prefix directory failed");
                    }

                    error = ensureBootstrapCompatPrefixSymlink();
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    error = ensureAptCacheConfig();
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    error = ensureBootstrapFilePermissions();
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    String abiMismatch = getBootstrapAbiMismatchMessage();
                    if (abiMismatch != null) {
                        showBootstrapErrorDialog(activity, whenDone, abiMismatch);
                        return;
                    }

                    Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");

                    // Recreate env file since termux prefix was wiped earlier
                    TermuxShellEnvironment.writeEnvironmentToFile(activity);

                    activity.runOnUiThread(whenDone);

                } catch (final Exception e) {
                    showBootstrapErrorDialog(activity, whenDone, Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)));

                } finally {
                    activity.runOnUiThread(() -> {
                        try {
                            progress.dismiss();
                        } catch (RuntimeException e) {
                            // Activity already dismissed - ignore.
                        }
                    });
                }
            }
        }.start();
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        Logger.logErrorExtended(LOG_TAG, "Bootstrap Error:\n" + message);

        // Send a notification with the exception so that the user knows why bootstrap setup failed
        sendBootstrapCrashReportNotification(activity, message);

        activity.runOnUiThread(() -> {
            try {
                String details = DataUtils.getTruncatedCommandOutput(message, 4000, false, true, true);
                String body = activity.getString(R.string.bootstrap_error_body);
                if (details != null && !details.isEmpty()) {
                    body = body + "\n\n" + details;
                }
                new AlertDialog.Builder(activity).setTitle(R.string.bootstrap_error_title).setMessage(body)
                    .setNegativeButton(R.string.bootstrap_error_abort, (dialog, which) -> {
                        dialog.dismiss();
                        activity.finish();
                    })
                    .setPositiveButton(R.string.bootstrap_error_try_again, (dialog, which) -> {
                        dialog.dismiss();
                        FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                        TermuxInstaller.setupBootstrapIfNeeded(activity, whenDone);
                    }).show();
            } catch (WindowManager.BadTokenException e1) {
                // Activity already dismissed - ignore.
            }
        });
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        final String title = TermuxConstants.TERMUX_APP_NAME + " Bootstrap Error";

        // Add info of all install Termux plugin apps as well since their target sdk or installation
        // on external/portable sd card can affect Termux app files directory access or exec.
        TermuxCrashUtils.sendCrashReportNotification(activity, LOG_TAG,
            title, null, "## " + title + "\n\n" + message + "\n\n" +
                TermuxUtils.getTermuxDebugMarkdownString(activity),
            true, false, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES, true);
    }

    static void setupStorageSymlinks(final Context context) {
        final String LOG_TAG = "termux-storage";
        final String title = TermuxConstants.TERMUX_APP_NAME + " Setup Storage Error";

        Logger.logInfo(LOG_TAG, "Setting up storage symlinks.");

        new Thread() {
            public void run() {
                try {
                    Error error;
                    File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;

                    error = FileUtils.clearDirectory("~/storage", storageDir.getAbsolutePath());
                    if (error != null) {
                        Logger.logErrorAndShowToast(context, LOG_TAG, error.getMessage());
                        Logger.logErrorExtended(LOG_TAG, "Setup Storage Error\n" + error.toString());
                        TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                            "## " + title + "\n\n" + Error.getErrorMarkdownString(error),
                            true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true);
                        return;
                    }

                    Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/shared, ~/storage/downloads, ~/storage/dcim, ~/storage/pictures, ~/storage/music and ~/storage/movies for directories in \"" + Environment.getExternalStorageDirectory().getAbsolutePath() + "\".");

                    // Get primary storage root "/storage/emulated/0" symlink
                    File sharedDir = Environment.getExternalStorageDirectory();
                    Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                    File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    Os.symlink(documentsDir.getAbsolutePath(), new File(storageDir, "documents").getAbsolutePath());

                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());

                    File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());

                    File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());

                    File podcastsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
                    Os.symlink(podcastsDir.getAbsolutePath(), new File(storageDir, "podcasts").getAbsolutePath());

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        File audiobooksDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS);
                        Os.symlink(audiobooksDir.getAbsolutePath(), new File(storageDir, "audiobooks").getAbsolutePath());
                    }

                    // Dir 0 should ideally be for primary storage
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ContextImpl.java;l=818
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=219
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=181
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/StorageManagerService.java;l=3796
                    // https://cs.android.com/android/platform/superproject/+/android-7.0.0_r36:frameworks/base/services/core/java/com/android/server/MountService.java;l=3053

                    // Create "Android/data/com.termux" symlinks
                    File[] dirs = context.getExternalFilesDirs(null);
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "external-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }

                    // Create "Android/media/com.termux" symlinks
                    dirs = context.getExternalMediaDirs();
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "media-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }

                    Logger.logInfo(LOG_TAG, "Storage symlinks created successfully.");
                } catch (Exception e) {
                    Logger.logErrorAndShowToast(context, LOG_TAG, e.getMessage());
                    Logger.logStackTraceWithMessage(LOG_TAG, "Setup Storage Error: Error setting up link", e);
                    TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                        "## " + title + "\n\n" + Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)),
                        true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true);
                }
            }
        }.start();
    }

    private static Error ensureDirectoryExists(File directory) {
        return FileUtils.createDirectoryFile(directory.getAbsolutePath());
    }

    private static Error ensureBootstrapCompatPrefixSymlink() {
        String compatPrefix = TermuxConstants.TERMUX_BOOTSTRAP_COMPAT_PREFIX_DIR_PATH;
        if (TERMUX_PREFIX_DIR_PATH.equals(compatPrefix)) return null;
        return FileUtils.createSymlinkFile("termux compat prefix", TERMUX_PREFIX_DIR_PATH, compatPrefix);
    }

    private static Error ensureAptCacheConfig() {
        if (!TermuxBootstrap.isAppPackageManagerAPT()) return null;

        String cacheDirPath = TermuxConstants.TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH + "/cache/apt";
        Error error = FileUtils.createDirectoryFile(cacheDirPath + "/archives/partial");
        if (error != null) return error;

        String aptConfPath = TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/apt/apt.conf";
        String aptConfContents = "# Auto-generated to override apt cache path for custom package name\n"
            + "Dir::Cache \"" + cacheDirPath + "\";\n"
            + "Dir::Cache::archives \"archives\";\n"
            + "Dir::Cache::archives::partial \"archives/partial\";\n";

        boolean shouldWrite = true;
        if (FileUtils.fileExists(aptConfPath, false)) {
            StringBuilder existing = new StringBuilder();
            Error readError = FileUtils.readTextFromFile("apt conf", aptConfPath, StandardCharsets.UTF_8, existing, false);
            if (readError == null) {
                String contents = existing.toString();
                if (contents.contains(cacheDirPath)) {
                    shouldWrite = false;
                } else if (contents.contains("Dir::Cache") && !contents.contains("/data/data/com.termux/cache")) {
                    shouldWrite = false;
                }
            }
        }

        if (shouldWrite) {
            return FileUtils.writeTextToFile("apt conf", aptConfPath, StandardCharsets.UTF_8, aptConfContents, false);
        }

        return null;
    }

    private static Error ensureBootstrapFilePermissions() {
        Error error;

        String[] dirs = new String[] {
            TermuxConstants.TERMUX_PREFIX_DIR_PATH,
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH,
            TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH,
            TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH,
            TermuxConstants.TERMUX_LIBEXEC_PREFIX_DIR_PATH,
            TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH,
            TermuxConstants.TERMUX_VAR_PREFIX_DIR_PATH
        };

        for (String dir : dirs) {
            error = FileUtils.validateDirectoryFileExistenceAndPermissions("termux directory", dir,
                TermuxConstants.TERMUX_PREFIX_DIR_PATH, false, FileUtils.APP_WORKING_DIRECTORY_PERMISSIONS,
                true, true, false, false);
            if (error != null) return error;
        }

        String[] execFiles = new String[] {
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/dash",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/pkg"
        };

        for (String file : execFiles) {
            error = ensureExecutableOrSymlink(file);
            if (error != null) return error;
        }

        String[] readFiles = new String[] {
            TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/profile",
            TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/termux-login.sh"
        };

        for (String file : readFiles) {
            error = FileUtils.validateRegularFileExistenceAndPermissions("termux config", file,
                TermuxConstants.TERMUX_PREFIX_DIR_PATH, "r--", true, true, false);
            if (error != null) return error;
        }

        return null;
    }

    private static Error ensureExecutableOrSymlink(String filePath) {
        if (FileUtils.symlinkFileExists(filePath)) {
            String targetPath = FileUtils.getCanonicalPath(filePath, null);
            return FileUtils.validateRegularFileExistenceAndPermissions("termux executable target", targetPath,
                TermuxConstants.TERMUX_PREFIX_DIR_PATH, FileUtils.APP_EXECUTABLE_FILE_PERMISSIONS,
                true, true, false);
        }

        return FileUtils.validateRegularFileExistenceAndPermissions("termux executable", filePath,
            TermuxConstants.TERMUX_PREFIX_DIR_PATH, FileUtils.APP_EXECUTABLE_FILE_PERMISSIONS,
            true, true, false);
    }

    public static byte[] loadZipBytes() {
        // Only load the shared library when necessary to save memory usage.
        System.loadLibrary("termux-bootstrap");
        return getZip();
    }

    public static native byte[] getZip();

    private static boolean isBootstrapFilesValid() {
        List<String> requiredFiles = new ArrayList<>(Arrays.asList(
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login",
            TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/profile",
            TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/tls/cert.pem"
        ));

        if (TermuxBootstrap.isAppPackageManagerAPT()) {
            requiredFiles.add(TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH + "/apt/sources.list");
        }

        for (String path : requiredFiles) {
            if (!FileUtils.fileExists(path, true)) {
                Logger.logError(LOG_TAG, "Missing bootstrap file: " + path);
                return false;
            }

            File file = new File(path);
            if (file.isFile() && file.length() == 0) {
                Logger.logError(LOG_TAG, "Bootstrap file is empty: " + path);
                return false;
            }
        }

        if (!isScriptShebangCompatible(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login")) {
            return false;
        }
        if (!isScriptShebangCompatible(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/pkg")) {
            return false;
        }
        if (!isBootstrapBinaryAbiSupported(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash")) {
            return false;
        }

        return true;
    }

    private static boolean isScriptShebangCompatible(String filePath) {
        String line = readFirstLine(filePath);
        if (line == null || line.isEmpty()) {
            Logger.logError(LOG_TAG, "Failed to read bootstrap script: " + filePath);
            return false;
        }

        if (!line.startsWith("#!")) return true;

        String interpreter = line.substring(2).trim();
        int spaceIndex = interpreter.indexOf(' ');
        if (spaceIndex > 0) {
            interpreter = interpreter.substring(0, spaceIndex);
        }

        String prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
        String compatPrefix = TermuxConstants.TERMUX_BOOTSTRAP_COMPAT_PREFIX_DIR_PATH;
        if (interpreter.startsWith(prefix) || interpreter.startsWith(compatPrefix)) {
            return true;
        }

        Logger.logError(LOG_TAG, "Bootstrap shebang mismatch: " + filePath + " -> " + interpreter);
        return false;
    }

    private static String readFirstLine(String filePath) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)))) {
            return reader.readLine();
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to read file \"" + filePath + "\": " + e.getMessage());
            return null;
        }
    }

    private static boolean isBootstrapBinaryAbiSupported(String filePath) {
        String mismatch = getBootstrapAbiMismatchMessage(filePath);
        if (mismatch != null) {
            Logger.logError(LOG_TAG, mismatch);
            return false;
        }
        return true;
    }

    private static String getBootstrapAbiMismatchMessage() {
        return getBootstrapAbiMismatchMessage(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash");
    }

    private static String getBootstrapAbiMismatchMessage(String filePath) {
        ElfInfo info = readElfInfo(filePath);
        if (info == null) {
            return "Failed to read ELF header for bootstrap binary: " + filePath;
        }

        String[] deviceAbis = getDeviceSupportedAbis();
        if (isElfCompatibleWithAbis(info, deviceAbis)) return null;

        return "Bootstrap CPU ABI mismatch.\n"
            + "- Detected bootstrap ABI: " + getElfAbiName(info) + "\n"
            + "- Device ABIs: " + Arrays.toString(deviceAbis) + "\n"
            + "Rebuild the APK for the target ABI and reinstall.";
    }

    private static String[] getDeviceSupportedAbis() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS;
        }
        if (Build.CPU_ABI2 != null && !Build.CPU_ABI2.isEmpty()) {
            return new String[] { Build.CPU_ABI, Build.CPU_ABI2 };
        }
        return new String[] { Build.CPU_ABI };
    }

    private static boolean isElfCompatibleWithAbis(ElfInfo info, String[] abis) {
        if (abis == null || abis.length == 0) return false;
        for (String abi : abis) {
            if ("arm64-v8a".equals(abi)) {
                if (info.eMachine == 183 && info.elfClass == 2) return true;
            } else if ("armeabi-v7a".equals(abi) || "armeabi".equals(abi)) {
                if (info.eMachine == 40 && info.elfClass == 1) return true;
            } else if ("x86_64".equals(abi)) {
                if (info.eMachine == 62 && info.elfClass == 2) return true;
            } else if ("x86".equals(abi)) {
                if (info.eMachine == 3 && info.elfClass == 1) return true;
            }
        }
        return false;
    }

    private static String getElfAbiName(ElfInfo info) {
        if (info.eMachine == 183) return (info.elfClass == 2) ? "arm64-v8a" : "arm64";
        if (info.eMachine == 40) return "armeabi-v7a";
        if (info.eMachine == 62) return "x86_64";
        if (info.eMachine == 3) return "x86";
        return "unknown(machine=" + info.eMachine + ", class=" + info.elfClass + ")";
    }

    private static ElfInfo readElfInfo(String filePath) {
        byte[] header = new byte[20];
        try (FileInputStream input = new FileInputStream(filePath)) {
            int read = input.read(header);
            if (read < header.length) return null;
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to read ELF header for \"" + filePath + "\": " + e.getMessage());
            return null;
        }

        if (header[0] != 0x7f || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
            return null;
        }

        int elfClass = header[4] & 0xff;
        int elfData = header[5] & 0xff;
        int eMachine;
        if (elfData == 2) {
            eMachine = ((header[18] & 0xff) << 8) | (header[19] & 0xff);
        } else {
            eMachine = (header[18] & 0xff) | ((header[19] & 0xff) << 8);
        }

        return new ElfInfo(elfClass, elfData, eMachine);
    }

    private static class ElfInfo {
        final int elfClass;
        final int elfData;
        final int eMachine;

        ElfInfo(int elfClass, int elfData, int eMachine) {
            this.elfClass = elfClass;
            this.elfData = elfData;
            this.eMachine = eMachine;
        }
    }

}
