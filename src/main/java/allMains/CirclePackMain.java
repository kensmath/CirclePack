package allMains;

import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import circlePack.PackControl;
import frames.OwlSplashScreen;

/**
 * Single-process entry point for GUI based CirclePack, 
 * using OwlSplashScreen while initiating.
 *
 * As of 7/2026, using Java 17+ (versus previous 1.8).
 */
public class CirclePackMain {

    public static void main(String[] args) {
        // System.out.println("[timing] main(): entered at " + System.currentTimeMillis());
        // Parse command-line args up front, same logic as
        // CP_after_Splash.main() used to do. These fields (CPBase.directory,
        // CPBase.initialScript, CPBase.socketActive, CPBase.cpSocketPort)
        // are read later inside startCirclePack(), so they must be set
        // before done() runs below - doing it here, before the splash
        // even shows, guarantees that.
    	parseArgs(args);

        SwingUtilities.invokeLater(() -> {
            OwlSplashScreen splash = new OwlSplashScreen();
            // Guarantee the splash stays on top even though PackControl's
            // real window becomes visible (via resetDisplay()) partway
            // through the background work below - otherwise the OS/window
            // manager could bring that window to front over the splash.

            splash.setAlwaysOnTop(true);
            splash.setVisible(true);
            // Lets PackControl.openBrowserFrame() report progress on this
            // same splash (instead of a separate popup) if something
            // during startup - e.g. a startup script's execute-on-load
            // command - needs the browser before it's warmed up.
            PackControl.startupSplash = splash;

            SwingWorker<CirclePack, ProgressUpdate> worker = new SwingWorker<>() {
                @Override
                protected CirclePack doInBackground() throws Exception {
                    // System.out.println("[timing] doInBackground(): entered at " + System.currentTimeMillis()
                    //         + " on thread " + Thread.currentThread().getName());
                    // Kick off the shared CefApp build in the background -
                    // NOT awaited here. This used to call CPBase.getCefApp()
                    // directly, which blocks for the whole CEF native
                    // bootstrap (~15s of "LOCATING / INITIALIZING /
                    // INITIALIZED"), fully serializing before "new
                    // CirclePack(1)" below could even start - exactly the
                    // startup delay this was meant to avoid, and why the
                    // splash bar looked frozen (no publish() calls happen
                    // while that block runs). warmUpCefApp() returns
                    // immediately and finishes the build on its own
                    // background thread; BrowserFrame is only constructed
                    // lazily on first use (see
                    // PackControl.openBrowserFrame()), by which point this
                    // has very likely already finished.
                    CPBase.warmUpCefApp();
                    // System.out.println("[timing] doInBackground(): warmUpCefApp() call returned at "
                    //         + System.currentTimeMillis());

                    // "new CirclePack(1)" (-> PackControl.initPackControl())
                    // is one long, uninstrumented block of setup work with
                    // no natural percentage checkpoints inside it - that's
                    // the ~12 seconds users actually wait through. Reporting
                    // it as "busy" keeps the bar visibly animating for that
                    // whole stretch instead of sitting frozen at 40%.
                    publish(ProgressUpdate.busy("   Starting CirclePack..."));
                    CirclePack circlePack = new CirclePack(1);
                    // System.out.println("[timing] doInBackground(): new CirclePack(1) returned at "
                    //         + System.currentTimeMillis());
                    publish(ProgressUpdate.at("Loading script...", 80));
                    return circlePack;
                }

                @Override
                protected void process(List<ProgressUpdate> chunks) {
                    ProgressUpdate latest = chunks.get(chunks.size() - 1);
                    if (latest.busy()) {
                        splash.setBusy(latest.message());
                    } else {
                        splash.setStatus(latest.message(), latest.percent());
                    }
                }

                @Override
                protected void done() {
                    // System.out.println("[timing] done(): entered at " + System.currentTimeMillis());
                    try {
                        CirclePack circlePack = get(); // re-throws doInBackground()'s exception, if any
                        System.out.println("CirclePack started\n");
                        circlePack.startCirclePack();
                        // System.out.println("[timing] done(): startCirclePack() returned at " + System.currentTimeMillis());
                        PackControl.scriptManager.populateDisplay();
                        // System.out.println("[timing] done(): populateDisplay() returned at " + System.currentTimeMillis());
                        splash.setStatus("Ready", 100);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // TODO: show an error dialog rather than failing silently
                    } finally {
                        // Startup's over (successfully or not) - openBrowserFrame()
                        // goes back to its own popup if the browser is opened later.
                        PackControl.startupSplash = null;
                        // after the window is actually populated, the splash comes down.
                        splash.dispose();
                        // System.out.println("[timing] done(): splash disposed at " + System.currentTimeMillis());
                    }
                }
            };
            worker.execute();
        });
    }

    // parse any initial arguments.
    private static void parseArgs(String[] args) {
        if (args.length >= 1) {
            for (int j = 0; j < args.length; j++) {
                if (args[j].equals("-dir") && args.length > j + 1) {
                    CPBase.directory = args[j + 1];
                    j++;
                } else if (args[j].startsWith("-scr") && args.length > j + 1) {
                    CPBase.initialScript = args[j + 1];
                    j++;
                } else if (args[j].equals("-socket")) {
                    CPBase.socketActive = true;
                    int prt = 3736;
                    try {
                        prt = Integer.parseInt(args[j + 1]);
                        CPBase.cpSocketPort = prt;
                        j++;
                    } catch (Exception ex) {
                        prt = 3736;
                    }
                }
                //AF>>>//
                // Opt-in only: by default the command socket is bound to
                // loopback (127.0.0.1), so only processes on this same
                // machine can connect. This flag allows connections from
                // other machines (e.g. over a LAN) -- only pass it on a
                // network you trust, since the socket protocol has no
                // authentication and grants full command execution. An
                // SSH tunnel (or similar) to the loopback-bound socket is
                // a safer way to reach it remotely and needs no flag here.
                else if (args[j].equals("-socket-remote")) {
                    CPBase.socketAllowRemote = true;
                }
                //<<<AF//
                else if (j == args.length - 1) {
                    CPBase.initialScript = args[j];
                }
            }
        }
    }

    /** percent is meaningless (and ignored) when busy is true. */
    private record ProgressUpdate(String message, int percent, boolean busy) {
        static ProgressUpdate at(String message, int percent) {
            return new ProgressUpdate(message, percent, false);
        }
        static ProgressUpdate busy(String message) {
            return new ProgressUpdate(message, 0, true);
        }
    }
}
