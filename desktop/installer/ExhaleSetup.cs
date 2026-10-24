/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Exhale's own installer: one native window, drawn by hand, instead of the stock WiX wizard that
 * jpackage produces. Built with the C# compiler every Windows ships (C# 5 / .NET Framework 4.8), so
 * it needs no SDK, starts instantly and carries the app image inside itself as payload.zip.
 *
 * The same source builds two programs. Without UNINSTALLER it is "Exhale Setup", which installs.
 * With it, it is "Uninstall Exhale", which is small (no payload) and is what Windows' Installed apps
 * list runs. See desktop/scripts/build-installer.ps1.
 */

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using Microsoft.Win32;
using Path = System.Windows.Shapes.Path;

// What Task Manager and a file's properties call these programs.
#if UNINSTALLER
[assembly: AssemblyTitle("Uninstall Exhale")]
#else
[assembly: AssemblyTitle("Exhale Setup")]
#endif
[assembly: AssemblyProduct("Exhale")]
[assembly: AssemblyCompany("ozyern")]
[assembly: AssemblyVersion(ExhaleSetup.Product.Version)]
[assembly: AssemblyFileVersion(ExhaleSetup.Product.Version)]

namespace ExhaleSetup
{
    static partial class Product
    {
        public const string Name = "Exhale";
        public const string Publisher = "ozyern";
        public const string Tagline = "YouTube Music, exhaled.";
        public const string Url = "https://github.com/ozyern/Exhale";
        public const string LicenceUrl = "https://www.gnu.org/licenses/gpl-3.0.html";
        public const string PrivacyUrl = "https://github.com/ozyern/Exhale/blob/master/PRIVACY.md";
        // The jpackage MSI's upgrade code. Earlier versions were installed with it; setup removes them.
        public const string LegacyUpgradeCode = "{7B3F2C1E-5A4D-4E8B-9C6F-2D1A0E9B8C47}";
        public const string UninstallKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\Exhale";
        public const string UninstallerName = "Uninstall Exhale.exe";

        public static string DefaultInstallDir
        {
            get { return System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "Exhale"); }
        }

        /** Settings, library, image cache and the sign-in browser: %APPDATA%\Exhale. */
        public static string DataDir
        {
            get { return System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "Exhale"); }
        }
    }

    public static class Program
    {
        [STAThread]
        public static int Main(string[] args)
        {
            bool quiet = args.Any(a => a == "--quiet" || a == "/S");
#if UNINSTALLER
            // Windows can't delete a running program, so the uninstaller copies itself out of the
            // folder it is about to remove and carries on from there.
            string installDir;
            int dirArg = Array.IndexOf(args, "--dir");
            if (dirArg >= 0 && dirArg + 1 < args.Length)
            {
                installDir = args[dirArg + 1];
            }
            else
            {
                installDir = System.IO.Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
                string temp = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "Exhale-Uninstall-" + Guid.NewGuid().ToString("N").Substring(0, 8) + ".exe");
                File.Copy(Assembly.GetExecutingAssembly().Location, temp, true);
                Process.Start(new ProcessStartInfo(temp, "--dir \"" + installDir + "\"" + (quiet ? " --quiet" : "")) { UseShellExecute = false });
                return 0;
            }
            if (quiet)
            {
                Installer.Uninstall(installDir, false, delegate { });
                Installer.ScheduleSelfDelete();
                return 0;
            }
            var app = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
            var window = new SetupWindow(true, installDir);
            int code = app.Run(window);
            Installer.ScheduleSelfDelete();
            return code;
#else
            if (quiet)
            {
                Installer.Install(new InstallOptions { Directory = Product.DefaultInstallDir, DesktopShortcut = true, StartMenuShortcut = true }, delegate { });
                return 0;
            }
            // --preview N opens page N without installing anything, to look at the design.
            int previewArg = Array.IndexOf(args, "--preview");
            int preview = -1;
            if (previewArg >= 0 && previewArg + 1 < args.Length) int.TryParse(args[previewArg + 1], out preview);
            var app = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
            return app.Run(new SetupWindow(false, Product.DefaultInstallDir, preview));
#endif
        }
    }

    // ───────────────────────────── The work ─────────────────────────────

    public class InstallOptions
    {
        public string Directory;
        public bool DesktopShortcut;
        public bool StartMenuShortcut;
    }

    /** Progress from the worker thread: 0..1, a headline and the file being handled. */
    public delegate void ProgressSink(double fraction, string headline, string detail);

    public static class Installer
    {
        [DllImport("msi.dll", CharSet = CharSet.Unicode)]
        static extern uint MsiEnumRelatedProducts(string upgradeCode, uint reserved, uint index, StringBuilder productCode);

        [DllImport("msi.dll", CharSet = CharSet.Unicode)]
        static extern uint MsiGetProductInfo(string product, string property, StringBuilder value, ref int length);

        const uint ErrorNoMoreItems = 259;

        public static Stream Resource(string name)
        {
            return Assembly.GetExecutingAssembly().GetManifestResourceStream(name);
        }

        static List<string> LegacyProducts()
        {
            var found = new List<string>();
            try
            {
                for (uint i = 0; ; i++)
                {
                    var code = new StringBuilder(39);
                    if (MsiEnumRelatedProducts(Product.LegacyUpgradeCode, 0, i, code) != 0) break;
                    found.Add(code.ToString());
                }
            }
            catch (Exception) { }
            return found;
        }

        /** What's installed now, for the "Existing" column: our own entry first, then an old MSI. */
        public static string ExistingVersion()
        {
            using (var key = Registry.CurrentUser.OpenSubKey(Product.UninstallKey))
            {
                if (key != null)
                {
                    var version = key.GetValue("DisplayVersion") as string;
                    if (!string.IsNullOrEmpty(version)) return version;
                }
            }
            foreach (var code in LegacyProducts())
            {
                var value = new StringBuilder(64);
                int length = value.Capacity;
                if (MsiGetProductInfo(code, "VersionString", value, ref length) == 0) return value.ToString();
            }
            return null;
        }

        public static string ExistingInstallDir()
        {
            using (var key = Registry.CurrentUser.OpenSubKey(Product.UninstallKey))
            {
                if (key == null) return null;
                return key.GetValue("InstallLocation") as string;
            }
        }

        /** Where the installed copy lives: our own entry, else the folder the old MSI used. */
        public static string InstalledDir()
        {
            string dir = ExistingInstallDir();
            if (!string.IsNullOrEmpty(dir) && Directory.Exists(dir)) return dir;
            string legacy = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Exhale");
            return Directory.Exists(legacy) ? legacy : null;
        }

        /** The Java runtime version of the copy in [dir], from the runtime's own release file. */
        public static string RuntimeVersionIn(string dir)
        {
            try
            {
                foreach (var line in File.ReadAllLines(System.IO.Path.Combine(dir, "runtime", "release")))
                {
                    if (line.StartsWith("JAVA_VERSION=")) return line.Substring("JAVA_VERSION=".Length).Trim('"');
                }
            }
            catch (Exception) { }
            return null;
        }

        /** libmpv's version in [dir], from the DLL's version resource ("v0.41.0-1023-g69e63f425" reads 0.41.0). */
        public static string MpvVersionIn(string dir)
        {
            try
            {
                var dll = Directory.GetFiles(dir, "libmpv-2.dll", SearchOption.AllDirectories).FirstOrDefault();
                if (dll == null) return null;
                var match = System.Text.RegularExpressions.Regex.Match(FileVersionInfo.GetVersionInfo(dll).FileVersion ?? "", @"\d+\.\d+(\.\d+)?");
                return match.Success ? match.Value : null;
            }
            catch (Exception) { return null; }
        }

        public static long PayloadSize()
        {
            using (var stream = Resource("payload.zip"))
            {
                if (stream == null) return 0;
                using (var zip = new ZipArchive(stream, ZipArchiveMode.Read))
                {
                    return zip.Entries.Sum(e => e.Length);
                }
            }
        }

        /** Closes Exhale if it's running from [dir] (or anywhere, for an old install): the launcher and the app it spawned. */
        static void CloseRunning(string dir)
        {
            foreach (var process in Process.GetProcessesByName("Exhale"))
            {
                try
                {
                    string path = null;
                    try { path = process.MainModule.FileName; } catch (Exception) { }
                    if (path != null && dir != null && !path.StartsWith(dir, StringComparison.OrdinalIgnoreCase)
                        && !path.StartsWith(System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Exhale"), StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }
                    if (process.MainWindowHandle != IntPtr.Zero) process.CloseMainWindow();
                    if (!process.WaitForExit(4000)) process.Kill();
                }
                catch (Exception) { }
            }
        }

        public static void Install(InstallOptions options, ProgressSink progress)
        {
            string dir = options.Directory;
            progress(0, "Getting ready", "Closing Exhale if it's open");
            CloseRunning(dir);

            // Versions before this installer came as an MSI. Remove it quietly so there's one Exhale.
            var legacy = LegacyProducts();
            foreach (var code in legacy)
            {
                progress(0.01, "Removing the previous version", code);
                try
                {
                    var msi = Process.Start(new ProcessStartInfo("msiexec.exe", "/x " + code + " /qn /norestart") { UseShellExecute = false, CreateNoWindow = true });
                    msi.WaitForExit(120000);
                }
                catch (Exception) { }
            }
            string legacyDir = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Exhale");
            if (legacy.Count > 0 && Directory.Exists(legacyDir) && !Directory.Exists(System.IO.Path.Combine(legacyDir, "runtime")))
            {
                // The MSI leaves behind what it didn't put there (the class-data archive). Nothing of the
                // listener's lives here — their data is in %APPDATA%\Exhale — so the folder can go.
                TryDelete(legacyDir);
            }

            // A clean copy: the previous version's jars and runtime are replaced, not merged.
            foreach (var sub in new[] { "app", "runtime" })
            {
                string old = System.IO.Path.Combine(dir, sub);
                if (Directory.Exists(old)) TryDelete(old);
            }
            Directory.CreateDirectory(dir);

            using (var stream = Resource("payload.zip"))
            using (var zip = new ZipArchive(stream, ZipArchiveMode.Read))
            {
                long total = Math.Max(1, zip.Entries.Sum(e => e.Length));
                long done = 0;
                var buffer = new byte[1 << 17];
                foreach (var entry in zip.Entries)
                {
                    string target = System.IO.Path.GetFullPath(System.IO.Path.Combine(dir, entry.FullName));
                    if (!target.StartsWith(System.IO.Path.GetFullPath(dir), StringComparison.OrdinalIgnoreCase)) continue;
                    if (entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\"))
                    {
                        Directory.CreateDirectory(target);
                        continue;
                    }
                    Directory.CreateDirectory(System.IO.Path.GetDirectoryName(target));
                    string headline = HeadlineFor(entry.FullName);
                    using (var input = entry.Open())
                    using (var output = new FileStream(target, FileMode.Create, FileAccess.Write, FileShare.None, 1 << 16))
                    {
                        int read;
                        long sinceReport = 0;
                        while ((read = input.Read(buffer, 0, buffer.Length)) > 0)
                        {
                            output.Write(buffer, 0, read);
                            done += read;
                            sinceReport += read;
                            if (sinceReport > (1 << 20))
                            {
                                sinceReport = 0;
                                progress(0.03 + 0.92 * done / total, headline, entry.Name);
                            }
                        }
                    }
                    progress(0.03 + 0.92 * done / total, headline, entry.Name);
                }
            }

            progress(0.96, "Finishing up", "Adding Exhale to Windows");
            string exe = System.IO.Path.Combine(dir, "Exhale.exe");
            using (var uninstaller = Resource("uninstaller.exe"))
            {
                if (uninstaller != null)
                {
                    using (var output = File.Create(System.IO.Path.Combine(dir, Product.UninstallerName))) uninstaller.CopyTo(output);
                }
            }

            string startMenu = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "Exhale.lnk");
            string desktop = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "Exhale.lnk");
            if (options.StartMenuShortcut) Shortcut(startMenu, exe, dir); else TryDeleteFile(startMenu);
            if (options.DesktopShortcut) Shortcut(desktop, exe, dir); else TryDeleteFile(desktop);

            using (var key = Registry.CurrentUser.CreateSubKey(Product.UninstallKey))
            {
                key.SetValue("DisplayName", Product.Name);
                key.SetValue("DisplayVersion", Product.Version);
                key.SetValue("Publisher", Product.Publisher);
                key.SetValue("DisplayIcon", exe + ",0");
                key.SetValue("InstallLocation", dir);
                key.SetValue("UninstallString", "\"" + System.IO.Path.Combine(dir, Product.UninstallerName) + "\"");
                key.SetValue("QuietUninstallString", "\"" + System.IO.Path.Combine(dir, Product.UninstallerName) + "\" --quiet");
                key.SetValue("URLInfoAbout", Product.Url);
                key.SetValue("HelpLink", Product.Url + "/issues");
                key.SetValue("InstallDate", DateTime.Now.ToString("yyyyMMdd"));
                key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                key.SetValue("EstimatedSize", (int)(DirectorySize(dir) / 1024), RegistryValueKind.DWord);
            }
            progress(1, "Done", "");
        }

        public static void Uninstall(string dir, bool removeData, ProgressSink progress)
        {
            progress(0, "Getting ready", "Closing Exhale if it's open");
            CloseRunning(dir);
            TryDeleteFile(System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "Exhale.lnk"));
            TryDeleteFile(System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "Exhale.lnk"));
            try { Registry.CurrentUser.DeleteSubKeyTree(Product.UninstallKey, false); } catch (Exception) { }

            var targets = new List<string>();
            if (Directory.Exists(dir)) targets.Add(dir);
            if (removeData && Directory.Exists(Product.DataDir)) targets.Add(Product.DataDir);
            var files = targets.SelectMany(t => SafeFiles(t)).ToList();
            int count = Math.Max(1, files.Count);
            for (int i = 0; i < files.Count; i++)
            {
                TryDeleteFile(files[i]);
                if (i % 25 == 0) progress(0.05 + 0.9 * i / count, files[i].StartsWith(Product.DataDir) ? "Removing your settings and library" : "Removing Exhale", System.IO.Path.GetFileName(files[i]));
            }
            foreach (var target in targets) TryDelete(target);
            progress(1, "Done", "");
        }

        /** The temporary copy of the uninstaller deletes itself a few seconds after it exits. */
        public static void ScheduleSelfDelete()
        {
            try
            {
                string self = Assembly.GetExecutingAssembly().Location;
                if (!self.StartsWith(System.IO.Path.GetTempPath(), StringComparison.OrdinalIgnoreCase)) return;
                Process.Start(new ProcessStartInfo("cmd.exe", "/c ping 127.0.0.1 -n 4 > nul & del /f /q \"" + self + "\"") { CreateNoWindow = true, UseShellExecute = false });
            }
            catch (Exception) { }
        }

        public static void Launch(string dir)
        {
            try { Process.Start(new ProcessStartInfo(System.IO.Path.Combine(dir, "Exhale.exe")) { WorkingDirectory = dir, UseShellExecute = true }); }
            catch (Exception) { }
        }

        static string HeadlineFor(string path)
        {
            string p = path.Replace('\\', '/').ToLowerInvariant();
            if (p.StartsWith("runtime/")) return "Unpacking the Java runtime";
            if (p.Contains("libmpv")) return "Unpacking the audio engine";
            if (p.Contains("skiko")) return "Unpacking the renderer";
            return "Copying Exhale";
        }

        static void Shortcut(string path, string target, string workingDir)
        {
            try
            {
                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                dynamic shell = Activator.CreateInstance(shellType);
                dynamic link = shell.CreateShortcut(path);
                link.TargetPath = target;
                link.WorkingDirectory = workingDir;
                link.IconLocation = target + ",0";
                link.Description = Product.Tagline;
                link.Save();
                Marshal.FinalReleaseComObject(link);
                Marshal.FinalReleaseComObject(shell);
            }
            catch (Exception) { }
        }

        static IEnumerable<string> SafeFiles(string dir)
        {
            try { return Directory.GetFiles(dir, "*", SearchOption.AllDirectories); }
            catch (Exception) { return new string[0]; }
        }

        static long DirectorySize(string dir)
        {
            return SafeFiles(dir).Sum(f => { try { return new FileInfo(f).Length; } catch (Exception) { return 0L; } });
        }

        static void TryDelete(string dir)
        {
            for (int attempt = 0; attempt < 5; attempt++)
            {
                try
                {
                    if (!Directory.Exists(dir)) return;
                    foreach (var f in SafeFiles(dir)) { try { File.SetAttributes(f, FileAttributes.Normal); } catch (Exception) { } }
                    Directory.Delete(dir, true);
                    return;
                }
                catch (Exception) { System.Threading.Thread.Sleep(400); }
            }
        }

        static void TryDeleteFile(string file)
        {
            try { if (File.Exists(file)) File.Delete(file); } catch (Exception) { }
        }
    }

    // ───────────────────────────── The look ─────────────────────────────

    static class Theme
    {
        public static readonly Color Ink = Color.FromRgb(0xF6, 0xF1, 0xF8);
        public static readonly Color Dim = Color.FromArgb(0xA8, 0xF6, 0xF1, 0xF8);
        public static readonly Color Faint = Color.FromArgb(0x5A, 0xF6, 0xF1, 0xF8);
        public static readonly Color Hairline = Color.FromArgb(0x26, 0xF6, 0xF1, 0xF8);
        public static readonly Color Accent = Color.FromRgb(0xF4, 0x9D, 0xC4);
        public static readonly Color Gold = Color.FromRgb(0xF7, 0xC6, 0x62);
        public static readonly Color Violet = Color.FromRgb(0x7B, 0x4D, 0xF2);

        public static FontFamily Font = new FontFamily("Segoe UI Variable Display, Segoe UI");
        public static FontFamily BodyFont = new FontFamily("Segoe UI Variable Text, Segoe UI");

        /** Exhale's own typeface, Linotte, for headings — unpacked to a temp folder because WPF loads fonts by URI. */
        public static void LoadFonts()
        {
            try
            {
                string folder = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "ExhaleSetup-fonts");
                Directory.CreateDirectory(folder);
                string file = System.IO.Path.Combine(folder, "linotte.ttf");
                if (!File.Exists(file))
                {
                    using (var input = Installer.Resource("linotte.ttf"))
                    using (var output = File.Create(file)) input.CopyTo(output);
                }
                var face = new GlyphTypeface(new Uri(file));
                string family = face.FamilyNames.Values.First();
                Font = new FontFamily(new Uri(folder + System.IO.Path.DirectorySeparatorChar), "./#" + family + ", Segoe UI Variable Display, Segoe UI");
            }
            catch (Exception) { }
        }

        public static SolidColorBrush Brush(Color color)
        {
            var brush = new SolidColorBrush(color);
            brush.Freeze();
            return brush;
        }

        public static TextBlock Text(string text, double size, FontWeight weight, Color color, bool heading = false)
        {
            return new TextBlock
            {
                Text = text,
                FontSize = size,
                FontWeight = weight,
                Foreground = Brush(color),
                FontFamily = heading ? Font : BodyFont,
                TextWrapping = TextWrapping.Wrap,
            };
        }

        public static ImageSource Logo()
        {
            try
            {
                var image = new BitmapImage();
                image.BeginInit();
                image.StreamSource = Installer.Resource("exhale.png");
                image.CacheOption = BitmapCacheOption.OnLoad;
                image.EndInit();
                image.Freeze();
                return image;
            }
            catch (Exception) { return null; }
        }

        public static readonly IEasingFunction Out = new CubicEase { EasingMode = EasingMode.EaseOut };
        public static readonly IEasingFunction InOut = new SineEase { EasingMode = EasingMode.EaseInOut };

        public static void Animate(Animatable target, DependencyProperty property, double to, int millis, IEasingFunction easing = null, int delay = 0)
        {
            var animation = new DoubleAnimation(to, TimeSpan.FromMilliseconds(millis)) { EasingFunction = easing ?? Out, BeginTime = TimeSpan.FromMilliseconds(delay) };
            target.BeginAnimation(property, animation);
        }

        public static void Animate(UIElement target, DependencyProperty property, double to, int millis, IEasingFunction easing = null, int delay = 0)
        {
            var animation = new DoubleAnimation(to, TimeSpan.FromMilliseconds(millis)) { EasingFunction = easing ?? Out, BeginTime = TimeSpan.FromMilliseconds(delay) };
            target.BeginAnimation(property, animation);
        }

        public static void AnimateColor(SolidColorBrush brush, Color to, int millis)
        {
            brush.BeginAnimation(SolidColorBrush.ColorProperty, new ColorAnimation(to, TimeSpan.FromMilliseconds(millis)) { EasingFunction = Out });
        }
    }

    /** A rounded button: outlined, filling with its colour under the pointer, sinking a little when pressed. */
    class PillButton : Border
    {
        public event Action Click;
        readonly SolidColorBrush fill;
        readonly Color color;
        readonly bool primary;
        readonly ScaleTransform scale = new ScaleTransform(1, 1);
        readonly TextBlock label;
        bool enabled = true;

        public PillButton(string text, bool primary)
        {
            this.primary = primary;
            color = primary ? Theme.Accent : Theme.Ink;
            fill = new SolidColorBrush(Color.FromArgb(primary ? (byte)0x1C : (byte)0x00, color.R, color.G, color.B));
            Background = fill;
            BorderBrush = Theme.Brush(Color.FromArgb(primary ? (byte)0xE6 : (byte)0x55, color.R, color.G, color.B));
            BorderThickness = new Thickness(1.4);
            CornerRadius = new CornerRadius(23);
            Height = 46;
            MinWidth = 128;
            Padding = new Thickness(26, 0, 26, 0);
            Margin = new Thickness(14, 0, 0, 0);
            Cursor = Cursors.Hand;
            RenderTransformOrigin = new Point(0.5, 0.5);
            RenderTransform = scale;
            label = Theme.Text(text, 15.5, FontWeights.SemiBold, primary ? Theme.Ink : Theme.Dim, true);
            label.VerticalAlignment = VerticalAlignment.Center;
            label.HorizontalAlignment = HorizontalAlignment.Center;
            label.TextWrapping = TextWrapping.NoWrap;
            Child = label;

            MouseEnter += delegate { if (enabled) Theme.AnimateColor(fill, Color.FromArgb(primary ? (byte)0x40 : (byte)0x16, color.R, color.G, color.B), 160); };
            MouseLeave += delegate
            {
                Theme.AnimateColor(fill, Color.FromArgb(primary ? (byte)0x1C : (byte)0x00, color.R, color.G, color.B), 220);
                Press(false);
            };
            MouseLeftButtonDown += delegate(object s, MouseButtonEventArgs e) { if (enabled) { Press(true); e.Handled = true; } };
            MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                if (!enabled) return;
                Press(false);
                e.Handled = true;
                if (Click != null) Click();
            };
        }

        public string Label { set { label.Text = value; } }

        public bool Enabled
        {
            get { return enabled; }
            set
            {
                enabled = value;
                Theme.Animate(this, OpacityProperty, value ? 1 : 0.35, 180);
                Cursor = value ? Cursors.Hand : Cursors.Arrow;
            }
        }

        void Press(bool down)
        {
            var spring = new ElasticEase { Oscillations = 1, Springiness = 6, EasingMode = EasingMode.EaseOut };
            Theme.Animate(scale, ScaleTransform.ScaleXProperty, down ? 0.96 : 1, down ? 90 : 420, down ? Theme.Out : spring);
            Theme.Animate(scale, ScaleTransform.ScaleYProperty, down ? 0.96 : 1, down ? 90 : 420, down ? Theme.Out : spring);
        }
    }

    /** A square checkbox with a tick that draws itself in. */
    class CheckRow : Grid
    {
        public event Action<bool> Changed;
        readonly Border box;
        readonly Path tick;
        readonly SolidColorBrush boxFill;
        bool isChecked;

        public CheckRow(string title, string detail, bool initial)
        {
            Cursor = Cursors.Hand;
            Background = Brushes.Transparent;
            Margin = new Thickness(0, 0, 0, 16);
            ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(38) });
            ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            boxFill = new SolidColorBrush(Colors.Transparent);
            box = new Border
            {
                Width = 22,
                Height = 22,
                CornerRadius = new CornerRadius(6),
                BorderThickness = new Thickness(1.5),
                BorderBrush = Theme.Brush(Theme.Faint),
                Background = boxFill,
                VerticalAlignment = VerticalAlignment.Top,
                HorizontalAlignment = HorizontalAlignment.Left,
                Margin = new Thickness(0, 1, 0, 0),
            };
            tick = new Path
            {
                Data = Geometry.Parse("M 5,11.5 L 9.5,16 L 17.5,7"),
                Stroke = Theme.Brush(Color.FromRgb(0x1A, 0x0F, 0x22)),
                StrokeThickness = 2.4,
                StrokeStartLineCap = PenLineCap.Round,
                StrokeEndLineCap = PenLineCap.Round,
                StrokeLineJoin = PenLineJoin.Round,
                StrokeDashArray = new DoubleCollection { 10, 10 },
                StrokeDashOffset = 10,
            };
            box.Child = new Canvas { Children = { tick } };
            Children.Add(box);

            var text = new StackPanel();
            text.Children.Add(Theme.Text(title, 15.5, FontWeights.SemiBold, Theme.Ink));
            if (detail != null)
            {
                var sub = Theme.Text(detail, 13, FontWeights.Normal, Theme.Dim);
                sub.Margin = new Thickness(0, 3, 0, 0);
                text.Children.Add(sub);
            }
            Grid.SetColumn(text, 1);
            Children.Add(text);

            MouseLeftButtonDown += delegate(object s, MouseButtonEventArgs e) { e.Handled = true; };
            MouseLeftButtonUp += delegate { Checked = !isChecked; };
            SetChecked(initial, false);
        }

        public bool Checked
        {
            get { return isChecked; }
            set
            {
                SetChecked(value, true);
                if (Changed != null) Changed(value);
            }
        }

        void SetChecked(bool value, bool animate)
        {
            isChecked = value;
            int ms = animate ? 260 : 0;
            Theme.AnimateColor(boxFill, value ? Theme.Accent : Colors.Transparent, ms);
            box.BorderBrush = Theme.Brush(value ? Theme.Accent : Theme.Faint);
            Theme.Animate(tick, Shape.StrokeDashOffsetProperty, value ? 0 : 10, animate ? 320 : 0, Theme.Out, value && animate ? 80 : 0);
        }
    }

    /** The glowing arc behind everything: it breathes in and out and turns very slowly, strewn with sparks. */
    class Aurora : Grid
    {
        public Aurora()
        {
            IsHitTestVisible = false;
            Background = new LinearGradientBrush(
                new GradientStopCollection
                {
                    new GradientStop(Color.FromRgb(0x06, 0x05, 0x10), 0),
                    new GradientStop(Color.FromRgb(0x13, 0x0A, 0x28), 0.52),
                    new GradientStop(Color.FromRgb(0x2A, 0x0C, 0x31), 1),
                },
                new Point(0, 0), new Point(1, 1));

            // A violet bloom low on the left, and a warmer one where the arc is brightest.
            Children.Add(Bloom(760, -300, 260, Color.FromArgb(0x60, 0x5A, 0x2C, 0xD6)));
            Children.Add(Bloom(620, 470, -260, Color.FromArgb(0x38, 0xF4, 0x9D, 0xC4)));

            const double size = 820;
            var ring = new Grid { Width = size, Height = size, HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(400, -330, 0, 0) };
            var arc = new LinearGradientBrush(
                new GradientStopCollection
                {
                    new GradientStop(Color.FromArgb(0x00, 0xF4, 0x9D, 0xC4), 0.10),
                    new GradientStop(Color.FromArgb(0xFF, 0xF4, 0x9D, 0xC4), 0.42),
                    new GradientStop(Color.FromArgb(0xFF, 0xF7, 0xC6, 0x62), 0.62),
                    new GradientStop(Color.FromArgb(0x00, 0x7B, 0x4D, 0xF2), 0.95),
                },
                new Point(0, 0.2), new Point(0.8, 1));
            ring.Children.Add(new Ellipse { Stroke = arc, StrokeThickness = 22, Opacity = 0.55, Effect = new BlurEffect { Radius = 34 } });
            ring.Children.Add(new Ellipse { Stroke = arc, StrokeThickness = 6, Opacity = 0.8, Effect = new BlurEffect { Radius = 6 } });
            ring.Children.Add(new Ellipse { Stroke = arc, StrokeThickness = 1.6 });

            // Sparks riding the arc.
            var random = new Random(17);
            var sparks = new Canvas();
            for (int i = 0; i < 70; i++)
            {
                double angle = (40 + random.NextDouble() * 200) * Math.PI / 180;
                double radius = size / 2 + (random.NextDouble() - 0.5) * 46;
                double d = 1.2 + random.NextDouble() * 2.6;
                var spark = new Ellipse { Width = d, Height = d, Fill = Theme.Brush(random.NextDouble() < 0.5 ? Theme.Ink : Theme.Gold), Opacity = 0 };
                Canvas.SetLeft(spark, size / 2 + Math.Cos(angle) * radius - d / 2);
                Canvas.SetTop(spark, size / 2 + Math.Sin(angle) * radius - d / 2);
                var twinkle = new DoubleAnimation(0, 0.4 + random.NextDouble() * 0.6, TimeSpan.FromMilliseconds(900 + random.Next(2400)))
                {
                    AutoReverse = true,
                    RepeatBehavior = RepeatBehavior.Forever,
                    BeginTime = TimeSpan.FromMilliseconds(random.Next(3000)),
                    EasingFunction = Theme.InOut,
                };
                spark.BeginAnimation(OpacityProperty, twinkle);
                sparks.Children.Add(spark);
            }
            ring.Children.Add(sparks);

            var breathe = new ScaleTransform(1, 1, size / 2, size / 2);
            var turn = new RotateTransform(0, size / 2, size / 2);
            ring.RenderTransform = new TransformGroup { Children = { breathe, turn } };
            // Exhale: in for five and a half seconds, out for five and a half.
            var breath = new DoubleAnimation(1, 1.045, TimeSpan.FromMilliseconds(5500)) { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever, EasingFunction = Theme.InOut };
            breathe.BeginAnimation(ScaleTransform.ScaleXProperty, breath);
            breathe.BeginAnimation(ScaleTransform.ScaleYProperty, breath);
            turn.BeginAnimation(RotateTransform.AngleProperty, new DoubleAnimation(0, 360, TimeSpan.FromSeconds(140)) { RepeatBehavior = RepeatBehavior.Forever });
            Children.Add(ring);
        }

        static Ellipse Bloom(double size, double left, double top, Color color)
        {
            return new Ellipse
            {
                Width = size,
                Height = size,
                HorizontalAlignment = HorizontalAlignment.Left,
                VerticalAlignment = VerticalAlignment.Top,
                Margin = new Thickness(left, top, 0, 0),
                Fill = new RadialGradientBrush(color, Color.FromArgb(0, color.R, color.G, color.B)),
            };
        }
    }

    /** The steps down the left edge; the current one's line reaches in from the window's edge and lights up. */
    class StepRail : StackPanel
    {
        readonly List<Rectangle> lines = new List<Rectangle>();
        readonly List<TextBlock> labels = new List<TextBlock>();

        public StepRail(string[] steps)
        {
            Margin = new Thickness(0, steps.Length > 4 ? 136 : 150, 0, 0);
            HorizontalAlignment = HorizontalAlignment.Left;
            VerticalAlignment = VerticalAlignment.Top;
            foreach (var step in steps)
            {
                // Five steps still clear the bottom edge.
                var row = new Grid { Height = steps.Length > 4 ? 72 : 84 };
                var line = new Rectangle { Height = 2.5, Width = 26, HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Center, RadiusX = 1.2, RadiusY = 1.2, Fill = new SolidColorBrush(Theme.Faint) };
                var label = Theme.Text(step, 17, FontWeights.SemiBold, Theme.Faint, true);
                label.Foreground = new SolidColorBrush(Theme.Faint);
                label.VerticalAlignment = VerticalAlignment.Center;
                label.Margin = new Thickness(52, 0, 0, 0);
                label.TextWrapping = TextWrapping.NoWrap;
                row.Children.Add(line);
                row.Children.Add(label);
                lines.Add(line);
                labels.Add(label);
                Children.Add(row);
            }
        }

        public void Select(int index)
        {
            for (int i = 0; i < lines.Count; i++)
            {
                bool current = i == index;
                Theme.Animate(lines[i], WidthProperty, current ? 40 : 26, 420);
                Theme.AnimateColor((SolidColorBrush)lines[i].Fill, current ? Theme.Accent : (i < index ? Theme.Dim : Theme.Faint), 320);
                Theme.AnimateColor((SolidColorBrush)labels[i].Foreground, current ? Theme.Ink : (i < index ? Theme.Dim : Theme.Faint), 320);
                labels[i].FontWeight = current ? FontWeights.Bold : FontWeights.SemiBold;
            }
        }
    }

    class SetupWindow : Window
    {
        readonly bool uninstall;
        string installDir;
        readonly Grid page;
        readonly StepRail rail;
        readonly PillButton primary;
        readonly PillButton secondary;
        FrameworkElement currentPage;
        int step;
        bool busy;
        bool finished;

        // Options
        CheckRow desktopShortcut, startMenuShortcut, launchAfter, removeData;
        TextBox pathBox;

        // Progress page
        TextBlock percent, headline, detail;
        Border progressFill;
        Grid progressTrack;

        readonly int preview;

        public SetupWindow(bool uninstall, string installDir, int preview = -1)
        {
            this.uninstall = uninstall;
            this.preview = preview;
            this.installDir = uninstall ? installDir : (Installer.ExistingInstallDir() ?? installDir);
            Theme.LoadFonts();

            Title = uninstall ? "Uninstall Exhale" : "Exhale Setup";
            Width = 880;
            Height = 600;
            WindowStyle = WindowStyle.None;
            ResizeMode = ResizeMode.NoResize;
            AllowsTransparency = true;
            Background = Brushes.Transparent;
            WindowStartupLocation = WindowStartupLocation.CenterScreen;
            UseLayoutRounding = true;
            TextOptions.SetTextFormattingMode(this, TextFormattingMode.Ideal);
            var logo = Theme.Logo();
            if (logo != null) Icon = logo;

            var frame = new Grid { Clip = new RectangleGeometry(new Rect(0, 0, 880, 600), 14, 14) };
            frame.Children.Add(new Aurora());
            frame.Children.Add(new Border { BorderBrush = Theme.Brush(Color.FromArgb(0x30, 0xFF, 0xFF, 0xFF)), BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(14), IsHitTestVisible = false });
            // Dragged by its header and the rail, never from the page, where a drag would swallow clicks.
            frame.MouseLeftButtonDown += delegate(object s, MouseButtonEventArgs e)
            {
                var at = e.GetPosition(frame);
                if (e.ButtonState == MouseButtonState.Pressed && (at.Y < 130 || at.X < 220)) { try { DragMove(); } catch (Exception) { } }
            };

            // Brand, top left.
            var brand = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(40, 34, 0, 0), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top };
            if (logo != null) brand.Children.Add(new Image { Source = logo, Width = 40, Height = 40, Margin = new Thickness(0, 0, 14, 0) });
            var name = Theme.Text("exhale", 32, FontWeights.Bold, Theme.Ink, true);
            name.VerticalAlignment = VerticalAlignment.Center;
            brand.Children.Add(name);
            var subtitle = Theme.Text(uninstall ? "Uninstall" : "Setup", 20, FontWeights.Normal, Theme.Dim, true);
            subtitle.VerticalAlignment = VerticalAlignment.Center;
            subtitle.Margin = new Thickness(14, 6, 0, 0);
            brand.Children.Add(subtitle);
            frame.Children.Add(brand);

            frame.Children.Add(CloseButton());

            rail = new StepRail(uninstall ? new[] { "Confirm", "Remove", "Done!" } : new[] { "Welcome", "Privacy", "Setup", "Install", "Done!" });
            frame.Children.Add(rail);

            page = new Grid { Margin = new Thickness(232, 142, 52, 104) };
            frame.Children.Add(page);

            frame.Children.Add(new Rectangle { Height = 1, Fill = Theme.Brush(Theme.Hairline), VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(232, 0, 52, 92) });

            var buttons = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Bottom, Margin = new Thickness(0, 0, 52, 28) };
            secondary = new PillButton("Cancel", false);
            primary = new PillButton("Next  ›", true);
            secondary.Click += Cancel;
            primary.Click += Next;
            buttons.Children.Add(secondary);
            buttons.Children.Add(primary);
            frame.Children.Add(buttons);

            Content = frame;

            // The window arrives the way the app's player does: up out of a little depth.
            var enter = new ScaleTransform(0.94, 0.94, 440, 300);
            frame.RenderTransform = enter;
            frame.Opacity = 0;
            Loaded += delegate
            {
                Theme.Animate(frame, OpacityProperty, 1, 320);
                var settle = new BackEase { Amplitude = 0.35, EasingMode = EasingMode.EaseOut };
                Theme.Animate(enter, ScaleTransform.ScaleXProperty, 1, 620, settle);
                Theme.Animate(enter, ScaleTransform.ScaleYProperty, 1, 620, settle);
                if (preview < 0)
                {
                    Show(0);
                    return;
                }
                if (preview >= DoneStep) finished = true;
                Show(Math.Min(preview, DoneStep));
                if (preview == ProgressStep)
                {
                    var later = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(900) };
                    later.Tick += delegate { later.Stop(); Report(0.62, "Unpacking the audio engine", "libmpv-2.dll"); };
                    later.Start();
                }
            };
            KeyDown += delegate(object s, KeyEventArgs e)
            {
                if (e.Key == Key.Escape) Cancel();
                if (e.Key == Key.Enter && primary.Enabled) Next();
            };
        }

        FrameworkElement CloseButton()
        {
            var fill = new SolidColorBrush(Colors.Transparent);
            var button = new Border { Width = 40, Height = 40, CornerRadius = new CornerRadius(20), Background = fill, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 22, 22, 0), Cursor = Cursors.Hand };
            button.Child = new Path { Data = Geometry.Parse("M 13,13 L 27,27 M 27,13 L 13,27"), Stroke = Theme.Brush(Theme.Ink), StrokeThickness = 1.8, StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round };
            button.MouseEnter += delegate { Theme.AnimateColor(fill, Color.FromArgb(0x22, 0xFF, 0xFF, 0xFF), 140); };
            button.MouseLeave += delegate { Theme.AnimateColor(fill, Colors.Transparent, 200); };
            button.MouseLeftButtonUp += delegate { Cancel(); };
            button.MouseLeftButtonDown += delegate(object s, MouseButtonEventArgs e) { e.Handled = true; };
            return button;
        }

        int ProgressStep { get { return uninstall ? 1 : 3; } }
        int DoneStep { get { return uninstall ? 2 : 4; } }

        void Cancel()
        {
            if (busy) return;
            if (finished && launchAfter != null && launchAfter.Checked) Installer.Launch(installDir);
            FadeOutAndClose();
        }

        void FadeOutAndClose()
        {
            var frame = (FrameworkElement)Content;
            var anim = new DoubleAnimation(0, TimeSpan.FromMilliseconds(200)) { EasingFunction = Theme.Out };
            anim.Completed += delegate { Close(); };
            frame.BeginAnimation(OpacityProperty, anim);
        }

        void Next()
        {
            if (busy) return;
            if (finished)
            {
                Cancel();
                return;
            }
            if (uninstall)
            {
                if (step == 0) { Show(1); RunUninstall(); }
            }
            else
            {
                if (step == 0) Show(1);
                else if (step == 1) Show(2);
                else if (step == 2 && ValidatePath()) { Show(3); RunInstall(); }
            }
        }

        void Show(int index)
        {
            step = index;
            rail.Select(index);
            FrameworkElement next;
            if (uninstall)
            {
                next = index == 0 ? ConfirmPage() : index == 1 ? ProgressPage("Removing Exhale") : DonePage();
            }
            else
            {
                next = index == 0 ? WelcomePage() : index == 1 ? PrivacyPage() : index == 2 ? OptionsPage() : index == 3 ? ProgressPage("Installing Exhale") : DonePage();
            }

            if (currentPage != null)
            {
                var old = currentPage;
                var leave = new TranslateTransform();
                old.RenderTransform = leave;
                Theme.Animate(leave, TranslateTransform.XProperty, -18, 180);
                var fade = new DoubleAnimation(0, TimeSpan.FromMilliseconds(160));
                fade.Completed += delegate { page.Children.Remove(old); };
                old.BeginAnimation(OpacityProperty, fade);
            }
            var arrive = new TranslateTransform(30, 0);
            next.RenderTransform = arrive;
            next.Opacity = 0;
            page.Children.Add(next);
            Theme.Animate(next, OpacityProperty, 1, 360, Theme.Out, 110);
            Theme.Animate(arrive, TranslateTransform.XProperty, 0, 560, new QuinticEase { EasingMode = EasingMode.EaseOut }, 90);
            // Each line of the page rises in a beat after the one above, like the app's lists.
            if (next is Panel)
            {
                int i = 0;
                foreach (UIElement child in ((Panel)next).Children)
                {
                    var rise = new TranslateTransform(0, 14);
                    child.RenderTransform = rise;
                    Theme.Animate(rise, TranslateTransform.YProperty, 0, 520, new QuinticEase { EasingMode = EasingMode.EaseOut }, 140 + i * 55);
                    i++;
                }
            }
            currentPage = next;

            primary.Label = uninstall
                ? (index == 0 ? "Uninstall" : "Finish")
                : (index <= 1 ? "Next  ›" : index == 2 ? "Install  ›" : "Finish");
            bool working = index == ProgressStep;
            primary.Enabled = !working;
            secondary.Enabled = !working;
            secondary.Visibility = index == DoneStep ? Visibility.Collapsed : Visibility.Visible;
        }

        StackPanel Heading(string title, string body)
        {
            var panel = new StackPanel();
            panel.Children.Add(Theme.Text(title, 30, FontWeights.Bold, Theme.Ink, true));
            if (body != null)
            {
                var text = Theme.Text(body, 15, FontWeights.Normal, Theme.Dim);
                text.Margin = new Thickness(0, 10, 0, 26);
                text.LineHeight = 23;
                panel.Children.Add(text);
            }
            return panel;
        }

        FrameworkElement WelcomePage()
        {
            var panel = Heading("Welcome to Exhale", Product.Tagline + " This puts Exhale " + Product.Version + " on your PC — no admin rights needed, and your settings and library stay where they are.");

            var table = new Grid { Margin = new Thickness(0, 0, 0, 22) };
            table.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            table.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(120) });
            table.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(110) });
            string existing = Installer.ExistingVersion();
            string installed = existing != null ? Installer.InstalledDir() : null;
            var rows = new[]
            {
                new[] { "What's included", "Version", "Installed" },
                new[] { "Exhale", Product.Version, existing ?? "—" },
                new[] { "JetBrains Runtime", Product.RuntimeVersion, (installed != null ? Installer.RuntimeVersionIn(installed) : null) ?? "—" },
                new[] { "libmpv audio engine", Product.MpvVersion, (installed != null ? Installer.MpvVersionIn(installed) : null) ?? "—" },
            };
            for (int r = 0; r < rows.Length; r++)
            {
                table.RowDefinitions.Add(new RowDefinition { Height = new GridLength(r == 0 ? 34 : 32) });
                for (int c = 0; c < 3; c++)
                {
                    bool header = r == 0;
                    var cell = Theme.Text(rows[r][c], header ? 13.5 : 15, header ? FontWeights.SemiBold : (c == 0 ? FontWeights.SemiBold : FontWeights.Normal), header ? Theme.Dim : (c == 0 ? Theme.Ink : Theme.Dim));
                    cell.VerticalAlignment = VerticalAlignment.Center;
                    cell.TextWrapping = TextWrapping.NoWrap;
                    Grid.SetRow(cell, r);
                    Grid.SetColumn(cell, c);
                    table.Children.Add(cell);
                }
            }
            var rule = new Rectangle { Height = 1, Fill = Theme.Brush(Theme.Hairline), VerticalAlignment = VerticalAlignment.Bottom };
            Grid.SetColumnSpan(rule, 3);
            table.Children.Add(rule);
            panel.Children.Add(table);

            if (existing != null)
            {
                panel.Children.Add(Note("Exhale " + existing + " is already installed. It will be replaced with " + Product.Version + "."));
            }

            var licence = new TextBlock { FontFamily = Theme.BodyFont, FontSize = 13, Foreground = Theme.Brush(Theme.Faint), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 6, 0, 0) };
            licence.Inlines.Add("Exhale is free software under the ");
            var link = new System.Windows.Documents.Hyperlink(new System.Windows.Documents.Run("GNU General Public License v3")) { Foreground = Theme.Brush(Theme.Accent), TextDecorations = null, Cursor = Cursors.Hand };
            link.Click += delegate { try { Process.Start(new ProcessStartInfo(Product.LicenceUrl) { UseShellExecute = true }); } catch (Exception) { } };
            licence.Inlines.Add(link);
            licence.Inlines.Add(".");
            panel.Children.Add(licence);
            return panel;
        }

        /** What Exhale talks to, shown before anything is installed: no account of its own, nothing collected. */
        FrameworkElement PrivacyPage()
        {
            var panel = Heading("Your privacy", "Exhale has no account of its own, no ads and no analytics. It only connects to the services that play and describe your music.");
            panel.Children[1].SetValue(MarginProperty, new Thickness(0, 10, 0, 18));
            panel.Children.Add(PrivacyRow("YouTube Music", "Search, streaming, and your library and playlists if you sign in."));
            panel.Children.Add(PrivacyRow("Lyrics, artwork and FLAC", "Lyric sites, Apple Music and Tidal (moving covers) and archive.org get a song's title and artist."));
            panel.Children.Add(PrivacyRow("Updates", "GitHub is asked for new releases. You can turn this off in Settings."));
            panel.Children.Add(PrivacyRow("Last.fm, ListenBrainz, Discord", "Off until you turn them on in Settings."));

            var more = new TextBlock { FontFamily = Theme.BodyFont, FontSize = 13, Foreground = Theme.Brush(Theme.Faint), TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 4, 0, 0) };
            more.Inlines.Add("Your settings and library stay on this PC. ");
            var link = new System.Windows.Documents.Hyperlink(new System.Windows.Documents.Run("Read the privacy policy")) { Foreground = Theme.Brush(Theme.Accent), TextDecorations = null, Cursor = Cursors.Hand };
            link.Click += delegate { try { Process.Start(new ProcessStartInfo(Product.PrivacyUrl) { UseShellExecute = true }); } catch (Exception) { } };
            more.Inlines.Add(link);
            more.Inlines.Add(".");
            panel.Children.Add(more);
            return panel;
        }

        static FrameworkElement PrivacyRow(string title, string detail)
        {
            var row = new Grid { Margin = new Thickness(0, 0, 0, 12) };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(22) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            row.Children.Add(new Ellipse { Width = 7, Height = 7, Fill = Theme.Brush(Theme.Accent), HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top, Margin = new Thickness(0, 7, 0, 0) });
            var text = new TextBlock { FontFamily = Theme.BodyFont, FontSize = 14, TextWrapping = TextWrapping.Wrap, LineHeight = 20 };
            text.Inlines.Add(new System.Windows.Documents.Run(title) { FontWeight = FontWeights.SemiBold, Foreground = Theme.Brush(Theme.Ink) });
            text.Inlines.Add(new System.Windows.Documents.Run("  " + detail) { Foreground = Theme.Brush(Theme.Dim) });
            Grid.SetColumn(text, 1);
            row.Children.Add(text);
            return row;
        }

        FrameworkElement Note(string text)
        {
            var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 10) };
            row.Children.Add(new Ellipse { Width = 7, Height = 7, Fill = Theme.Brush(Theme.Gold), Margin = new Thickness(0, 0, 10, 0), VerticalAlignment = VerticalAlignment.Center });
            var t = Theme.Text(text, 13.5, FontWeights.Normal, Theme.Dim);
            t.MaxWidth = 540;
            row.Children.Add(t);
            return row;
        }

        FrameworkElement OptionsPage()
        {
            var panel = Heading("Where should Exhale live?", null);
            panel.Children[0].SetValue(MarginProperty, new Thickness(0, 0, 0, 22));

            var field = new Grid { Height = 48, Margin = new Thickness(0, 0, 0, 10) };
            field.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            field.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var box = new Border { CornerRadius = new CornerRadius(12), Background = Theme.Brush(Color.FromArgb(0x14, 0xFF, 0xFF, 0xFF)), BorderBrush = Theme.Brush(Theme.Hairline), BorderThickness = new Thickness(1) };
            pathBox = new TextBox
            {
                Text = installDir,
                Background = Brushes.Transparent,
                BorderThickness = new Thickness(0),
                Foreground = Theme.Brush(Theme.Ink),
                CaretBrush = Theme.Brush(Theme.Accent),
                SelectionBrush = Theme.Brush(Theme.Accent),
                FontFamily = Theme.BodyFont,
                FontSize = 14.5,
                VerticalContentAlignment = VerticalAlignment.Center,
                Padding = new Thickness(14, 0, 14, 0),
            };
            box.Child = pathBox;
            field.Children.Add(box);
            var browse = new PillButton("Browse…", false) { Height = 48, MinWidth = 110, CornerRadius = new CornerRadius(12) };
            browse.Click += Browse;
            Grid.SetColumn(browse, 1);
            field.Children.Add(browse);
            panel.Children.Add(field);

            long need = Installer.PayloadSize();
            string space = "Needs " + FormatSize(need);
            try
            {
                var drive = new DriveInfo(System.IO.Path.GetPathRoot(System.IO.Path.GetFullPath(installDir)));
                space += " · " + FormatSize(drive.AvailableFreeSpace) + " free on " + drive.Name.TrimEnd('\\');
            }
            catch (Exception) { }
            var spaceText = Theme.Text(space, 13, FontWeights.Normal, Theme.Faint);
            spaceText.Margin = new Thickness(2, 0, 0, 26);
            panel.Children.Add(spaceText);

            desktopShortcut = desktopShortcut ?? new CheckRow("Put Exhale on the desktop", null, true);
            startMenuShortcut = startMenuShortcut ?? new CheckRow("Add Exhale to the Start menu", "So you can find it by searching \"Exhale\".", true);
            launchAfter = launchAfter ?? new CheckRow("Open Exhale when setup finishes", null, true);
            Detach(desktopShortcut); Detach(startMenuShortcut); Detach(launchAfter);
            panel.Children.Add(desktopShortcut);
            panel.Children.Add(startMenuShortcut);
            panel.Children.Add(launchAfter);
            return panel;
        }

        static void Detach(FrameworkElement element)
        {
            var parent = element.Parent as Panel;
            if (parent != null) parent.Children.Remove(element);
        }

        void Browse()
        {
            using (var dialog = new System.Windows.Forms.FolderBrowserDialog { Description = "Choose where to install Exhale", SelectedPath = pathBox.Text, ShowNewFolderButton = true })
            {
                if (dialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
                {
                    string chosen = dialog.SelectedPath;
                    // Choosing "D:\Apps" means "D:\Apps\Exhale", not scattering files into D:\Apps.
                    if (!chosen.TrimEnd('\\').EndsWith("Exhale", StringComparison.OrdinalIgnoreCase)) chosen = System.IO.Path.Combine(chosen, "Exhale");
                    pathBox.Text = chosen;
                }
            }
        }

        bool ValidatePath()
        {
            try
            {
                string dir = System.IO.Path.GetFullPath(pathBox.Text.Trim());
                Directory.CreateDirectory(dir);
                string probe = System.IO.Path.Combine(dir, ".exhale-write-test");
                File.WriteAllText(probe, "");
                File.Delete(probe);
                installDir = dir;
                return true;
            }
            catch (Exception)
            {
                MessageBox.Show(this, "Exhale can't be installed there. Pick a folder you can write to — the default, in your user folder, always works.", "Exhale Setup", MessageBoxButton.OK, MessageBoxImage.Warning);
                return false;
            }
        }

        FrameworkElement ConfirmPage()
        {
            var panel = Heading("Uninstall Exhale?", "This removes Exhale " + (Installer.ExistingVersion() ?? "") + " from " + installDir + ", along with its Start menu and desktop shortcuts.");
            removeData = new CheckRow("Also delete my settings, library and cache", "Everything in " + Product.DataDir + ". Leave this off to keep it for next time.", false);
            panel.Children.Add(removeData);
            return panel;
        }

        FrameworkElement ProgressPage(string title)
        {
            var panel = new StackPanel();
            panel.Children.Add(Theme.Text(title, 30, FontWeights.Bold, Theme.Ink, true));

            percent = Theme.Text("0%", 64, FontWeights.Light, Theme.Ink, true);
            percent.Margin = new Thickness(0, 36, 0, 6);
            panel.Children.Add(percent);

            progressTrack = new Grid { Height = 6, Margin = new Thickness(0, 6, 0, 18), ClipToBounds = true };
            progressTrack.Children.Add(new Border { CornerRadius = new CornerRadius(3), Background = Theme.Brush(Color.FromArgb(0x1E, 0xFF, 0xFF, 0xFF)) });
            progressFill = new Border
            {
                CornerRadius = new CornerRadius(3),
                HorizontalAlignment = HorizontalAlignment.Left,
                Width = 0,
                Background = new LinearGradientBrush(Theme.Violet, Theme.Accent, 0),
                Effect = new DropShadowEffect { Color = Theme.Accent, BlurRadius = 16, ShadowDepth = 0, Opacity = 0.8 },
            };
            // A glint that keeps travelling along the bar so it never looks stuck on a big file.
            var glint = new Rectangle
            {
                Width = 120,
                HorizontalAlignment = HorizontalAlignment.Left,
                Fill = new LinearGradientBrush(new GradientStopCollection { new GradientStop(Color.FromArgb(0, 255, 255, 255), 0), new GradientStop(Color.FromArgb(0x90, 255, 255, 255), 0.5), new GradientStop(Color.FromArgb(0, 255, 255, 255), 1) }, 0),
            };
            var glintMove = new TranslateTransform(-120, 0);
            glint.RenderTransform = glintMove;
            glintMove.BeginAnimation(TranslateTransform.XProperty, new DoubleAnimation(-120, 640, TimeSpan.FromMilliseconds(1600)) { RepeatBehavior = RepeatBehavior.Forever, EasingFunction = Theme.InOut });
            progressFill.Child = new Grid { ClipToBounds = true, Children = { glint } };
            progressTrack.Children.Add(progressFill);
            panel.Children.Add(progressTrack);

            headline = Theme.Text("Getting ready", 16, FontWeights.SemiBold, Theme.Ink);
            panel.Children.Add(headline);
            detail = Theme.Text("", 13, FontWeights.Normal, Theme.Faint);
            detail.TextWrapping = TextWrapping.NoWrap;
            detail.TextTrimming = TextTrimming.CharacterEllipsis;
            detail.Margin = new Thickness(0, 4, 0, 0);
            panel.Children.Add(detail);
            return panel;
        }

        FrameworkElement DonePage()
        {
            var panel = new StackPanel();

            // A ring that draws itself, then the tick inside it.
            var mark = new Grid { Width = 76, Height = 76, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 0, 0, 22) };
            double circumference = Math.PI * 72 / 3.0;
            var ring = new Ellipse { Stroke = new LinearGradientBrush(Theme.Accent, Theme.Gold, 45), StrokeThickness = 3, StrokeDashArray = new DoubleCollection { circumference, circumference }, StrokeDashOffset = circumference, Effect = new DropShadowEffect { Color = Theme.Accent, BlurRadius = 22, ShadowDepth = 0, Opacity = 0.7 } };
            var check = new Path { Data = Geometry.Parse("M 23,39 L 33,49 L 54,28"), Stroke = Theme.Brush(Theme.Ink), StrokeThickness = 3.4, StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round, StrokeLineJoin = PenLineJoin.Round, StrokeDashArray = new DoubleCollection { 16, 16 }, StrokeDashOffset = 16 };
            mark.Children.Add(ring);
            mark.Children.Add(check);
            Theme.Animate(ring, Shape.StrokeDashOffsetProperty, 0, 900, new QuinticEase { EasingMode = EasingMode.EaseInOut }, 200);
            Theme.Animate(check, Shape.StrokeDashOffsetProperty, 0, 420, Theme.Out, 900);
            panel.Children.Add(mark);

            if (uninstall)
            {
                panel.Children.Add(Theme.Text("Exhale has been removed", 30, FontWeights.Bold, Theme.Ink, true));
                var body = Theme.Text(removeData != null && removeData.Checked ? "Your settings and library were deleted too." : "Your settings and library are still in " + Product.DataDir + ", ready if you come back.", 15, FontWeights.Normal, Theme.Dim);
                body.Margin = new Thickness(0, 10, 0, 0);
                panel.Children.Add(body);
            }
            else
            {
                panel.Children.Add(Theme.Text("Exhale is ready", 30, FontWeights.Bold, Theme.Ink, true));
                var body = Theme.Text("Find it in the Start menu" + (desktopShortcut != null && desktopShortcut.Checked ? " or on your desktop" : "") + ". Take a breath, press play.", 15, FontWeights.Normal, Theme.Dim);
                body.Margin = new Thickness(0, 10, 0, 28);
                panel.Children.Add(body);
                launchAfter = launchAfter ?? new CheckRow("Open Exhale when setup finishes", null, true);
                Detach(launchAfter);
                panel.Children.Add(launchAfter);
            }
            return panel;
        }

        void Report(double fraction, string head, string file)
        {
            Dispatcher.BeginInvoke(new Action(delegate
            {
                if (percent == null) return;
                percent.Text = Math.Round(fraction * 100) + "%";
                headline.Text = head;
                detail.Text = file;
                double width = Math.Max(0, progressTrack.ActualWidth * fraction);
                progressFill.BeginAnimation(WidthProperty, new DoubleAnimation(width, TimeSpan.FromMilliseconds(240)) { EasingFunction = Theme.Out });
            }));
        }

        void RunInstall()
        {
            busy = true;
            var options = new InstallOptions { Directory = installDir, DesktopShortcut = desktopShortcut.Checked, StartMenuShortcut = startMenuShortcut.Checked };
            Task.Factory.StartNew(delegate
            {
                string error = null;
                try { Installer.Install(options, Report); }
                catch (Exception e) { error = e.Message; }
                Dispatcher.BeginInvoke(new Action(delegate { Finish(error); }));
            });
        }

        void RunUninstall()
        {
            busy = true;
            bool data = removeData != null && removeData.Checked;
            Task.Factory.StartNew(delegate
            {
                string error = null;
                try { Installer.Uninstall(installDir, data, Report); }
                catch (Exception e) { error = e.Message; }
                Dispatcher.BeginInvoke(new Action(delegate { Finish(error); }));
            });
        }

        void Finish(string error)
        {
            busy = false;
            if (error != null)
            {
                MessageBox.Show(this, (uninstall ? "Exhale couldn't be fully removed: " : "Exhale couldn't be installed: ") + error, Title, MessageBoxButton.OK, MessageBoxImage.Error);
                primary.Enabled = true;
                secondary.Enabled = true;
                return;
            }
            // Let the bar sit full for a moment before moving on.
            var hold = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(450) };
            hold.Tick += delegate
            {
                hold.Stop();
                finished = true;
                Show(DoneStep);
            };
            hold.Start();
        }

        static string FormatSize(long bytes)
        {
            double mb = bytes / 1024.0 / 1024.0;
            return mb >= 1024 ? (mb / 1024).ToString("0.0") + " GB" : Math.Round(mb) + " MB";
        }
    }
}
