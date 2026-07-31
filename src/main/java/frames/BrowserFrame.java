package frames;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Utilities;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.network.CefRequest;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import input.CommandStrParser;
import input.TrafficCenter;
import packing.PackData;
import packing.ReadWrite;

/**
 * A CirclePack browser window — one of these per open browser panel. Tabs
 * within the window share ONE CefClient/CefApp (this is CEF's normal
 * supported pattern for multi-browser-per-client apps — every callback
 * hands back the specific CefBrowser it's about to for, which is how the
 * handlers below tell tabs apart); the shared CefApp singleton itself
 * lives in CefAppHolder and is NOT created or disposed from this class.
 *
 * Layout (top to bottom):
 *   - window title (tracks the ACTIVE tab's page title)
 *   - toolbar: back / forward / reload / new-tab buttons, then an
 *     editable address combo box whose dropdown arrow shows previously
 *     visited addresses. The toolbar is shared by the whole window and
 *     always reflects whichever tab is currently active — switching tabs
 *     re-syncs the address bar text and the back/forward/reload state to
 *     that tab, and address-bar actions (typing + Enter, back, forward,
 *     reload) apply to the active tab.
 *   - tab strip + the active tab's CEF browser display area (JTabbedPane)
 *   - status bar: read-only field echoing the URL of whatever link the
 *     cursor is currently hovering over in the active tab (blank otherwise)
 *
 * Drop this in next to CirclePack's other *Frame window classes and adapt
 * to whatever base class / windowing convention they use (this sketch
 * extends JFrame directly for clarity).
 */
public class BrowserFrame extends JFrame {
	private static final long serialVersionUID = 1L;

	private static final String DEFAULT_TITLE = "CirclePack Browser";

	// Extensions that get handed off to CirclePack's own loading machinery
	// instead of being displayed as a web page (see ScriptPackingRequestHandler,
	// below). Matched case-insensitively against the URL's path only (query
	// string / fragment, if any, are ignored).
	private static final String[] SCRIPT_EXTENSIONS = {".cps", ".xmd", ".cmd"};
	private static final String[] PACKING_EXTENSIONS = {".p", ".q"};

	// Matches the old MemComboBox.MAX_MEM_LEN - how many entries the
	// persisted "recently visited" list keeps, most recent first.
	private static final int MAX_HISTORY_ENTRIES = 15;

    private final CefClient client; // shared by every tab in this window
    private final String homeUrl;   // what a new tab opens to
    private boolean browserClosed = false;

    // Where the shared "recently visited" address history is loaded from
    // at startup and saved to after every visit - CirclePack's
    // CPBase.WEB_URL_FILE, resolved to an absolute path by the caller
    // (see PackControl.initPackControl()). Null means "don't persist" -
    // e.g. for callers that just want a plain, in-memory-only browser.
    private final File historyFile;

    // Tabs
    private final List<BrowserTab> openTabs = new ArrayList<>();
    private JTabbedPane tabs;
    private BrowserTab activeTab; // kept in sync with tabs.getSelectedIndex()

    /** Everything genuinely per-tab: its own CefBrowser (with its own navigation history), display component, and tab-strip label. */
    private class BrowserTab {
        final CefBrowser browser;
        final Component ui;
        String title = "New Tab";
        JLabel headerLabel;

        BrowserTab(String url) {
            browser = client.createBrowser(url, false, false);
            ui = browser.getUIComponent();
        }
    }

    // Toolbar controls (shared across tabs; reflect whichever tab is active)
    private JButton backButton;
    private JButton forwardButton;
    private JButton reloadButton;
    private JButton newTabButton;
    private JComboBox<String> addressBar;
    private JTextField addressField; // the combo box's editor component
    private DefaultComboBoxModel<String> addressHistory; // shared "recently visited" list across all tabs in this window
    private boolean updatingAddressBar = false; // guards against feedback loops

    // Emacs-editing state for the address field
    private String killBuffer = "";
    private int historyBrowseIndex = -1; // -1 = not currently browsing history

    // Status bar (echoes hovered link in the active tab, blank when nothing is hovered)
    private JTextField statusBar;

    /**
     * Constructor
     * historyFilePath, if non-null/non-blank, is where the shared
     * "recently visited" address list is loaded from at construction and
     * saved to after every visit - CirclePack's CPBase.WEB_URL_FILE
     * preference (see PackControl.initPackControl(), which resolves it to
     * an absolute path before passing it in here).
     */
    public BrowserFrame(String startUrl, String historyFilePath) {
        super(DEFAULT_TITLE);
        this.homeUrl = startUrl;
        this.historyFile = (historyFilePath == null || historyFilePath.trim().isEmpty())
                ? null : new File(historyFilePath.trim());

        // One client for the whole window; every tab's 
        // CefBrowser is created from it, and its 
        // handlers (registered once, below) receive 
        // callbacks for ALL of them, distinguishing tabs 
        // by the CefBrowser each callback hands back 
        // (see tabFor()).
        client = CPBase.getCefApp().createClient();
        client.addDisplayHandler(new BrowserDisplayHandler());
        client.addLoadHandler(new BrowserLoadHandler());
        client.addRequestHandler(new ScriptPackingRequestHandler());

        // Clicking the window's X just HIDES it — it does NOT tear down the
        // CEF browser/client. Those are expensive, one-way-destructible
        // native resources (see closeBrowser() below): once closed they
        // cannot be reloaded or shown again. If CirclePack keeps a
        // reference to this frame and later "recalls" it (e.g. reopening
        // on a former page), setVisible(true) just works, with every tab's
        // page, in-page navigation history, and the shared address history
        // all intact. Use closeBrowser() explicitly — from CirclePack's
        // exit()/shutdown path, or from a "close this browser for good"
        // action — when you actually want to release the underlying
        // browser permanently.
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(900, 650);
        getContentPane().setLayout(new BorderLayout());

        tabs = new JTabbedPane();

        getContentPane().add(buildToolBar(), BorderLayout.NORTH); // also loads + populates addressHistory from historyFile, if any
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);

        openTab(startUrl, true); // also syncs the toolbar for this first tab
        tabs.addChangeListener(e -> onActiveTabChanged());
    }

    /**
     * Creates a new tab loaded to url, adds it to the tab strip with a
     * closable header, and — if select is true — makes it active. The
     * explicit onActiveTabChanged() call covers the case where
     * tabs.setSelectedIndex() doesn't actually change the index (e.g. the
     * very first tab, auto-selected by JTabbedPane before our
     * ChangeListener is even attached) and so wouldn't otherwise fire.
     */
    private BrowserTab openTab(String url, boolean select) {
        BrowserTab tab = new BrowserTab(url);
        openTabs.add(tab);
        int index = openTabs.size() - 1;
        tabs.addTab(tab.title, tab.ui);
        tabs.setTabComponentAt(index, buildTabHeader(tab));
        if (select) {
            tabs.setSelectedIndex(index);
            onActiveTabChanged();
        }
        return tab;
    }

    /**
     * Closes tab. Closing the WINDOW's only remaining tab is treated the
     * same as clicking the window's own X — it just hides the window
     * (see the HIDE_ON_CLOSE comment in the constructor) rather than
     * leaving a tab-less, useless window or destroying CEF resources that
     * closeBrowser() alone should be responsible for tearing down.
     *
     * Bookkeeping (openTabs/tabs) is updated BEFORE the underlying
     * browser.close(true) call so that tabFor() immediately stops
     * resolving this tab — any callbacks that arrive from it afterward
     * are harmlessly ignored rather than touching a detached tab header.
     */
    private void closeTab(BrowserTab tab) {
        int index = openTabs.indexOf(tab);
        if (index < 0) return;
        if (openTabs.size() == 1) {
            setVisible(false);
            return;
        }
        openTabs.remove(index);
        tabs.remove(index);
        onActiveTabChanged();
        tab.browser.close(true);
    }

    /** The standard "JLabel + close JButton" custom tab-strip header (see e.g. the Java Tutorial's closable-tabs pattern). */
    private Component buildTabHeader(BrowserTab tab) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.setOpaque(false);

        JLabel label = new JLabel(shortenTitle(tab.title));
        tab.headerLabel = label;

        JButton close = new JButton("×"); // ×
        close.setFont(close.getFont().deriveFont(Font.PLAIN, 12f));
        close.setMargin(new Insets(0, 4, 0, 4));
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusable(false);
        close.setToolTipText("Close Tab");
        close.addActionListener(e -> closeTab(tab));

        panel.add(label);
        panel.add(close);
        return panel;
    }

    private static String shortenTitle(String title) {
        if (title == null || title.isEmpty()) return "New Tab";
        return title.length() > 24 ? title.substring(0, 22) + "…" : title;
    }

    /** Finds which tab a CefBrowser from a handler callback belongs to, or null if it's already been closed. */
    private BrowserTab tabFor(CefBrowser browser) {
        for (BrowserTab t : openTabs) {
            if (t.browser == browser) return t;
        }
        return null;
    }

    /** Called whenever the selected tab changes (tab click, or programmatically from openTab()/closeTab()). */
    private void onActiveTabChanged() {
        int idx = tabs.getSelectedIndex();
        if (idx < 0 || idx >= openTabs.size()) {
            activeTab = null;
            return;
        }
        activeTab = openTabs.get(idx);
        refreshToolbarForActiveTab();
    }

    /** Re-syncs the shared toolbar (nav buttons, address bar, window title) to whatever the active tab is currently showing. */
    private void refreshToolbarForActiveTab() {
        if (activeTab == null) return;
        backButton.setEnabled(activeTab.browser.canGoBack());
        forwardButton.setEnabled(activeTab.browser.canGoForward());
        syncAddressBarToActiveTab();
        setTitle((activeTab.title == null || activeTab.title.isEmpty())
                ? DEFAULT_TITLE : activeTab.title + " - " + DEFAULT_TITLE);
    }

    /**
     * Builds the toolbar: nav + new-tab buttons on the left, address combo
     * box (with its built-in dropdown arrow doubling as a history picker)
     * filling the rest of the row.
     */
    private JPanel buildToolBar() {
        JPanel toolBar = new JPanel(new BorderLayout(4, 0));
        toolBar.setBorder(new EmptyBorder(4, 4, 4, 4));

        // --- nav buttons ---
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

        backButton = makeNavButton("◀", "Back");
        forwardButton = makeNavButton("▶", "Forward");
        reloadButton = makeNavButton("⟳", "Reload");
        newTabButton = makeNavButton("+", "New Tab");

        backButton.setEnabled(false);
        forwardButton.setEnabled(false);

        backButton.addActionListener(e -> { if (activeTab != null && activeTab.browser.canGoBack()) activeTab.browser.goBack(); });
        forwardButton.addActionListener(e -> { if (activeTab != null && activeTab.browser.canGoForward()) activeTab.browser.goForward(); });
        reloadButton.addActionListener(e -> { if (activeTab != null) activeTab.browser.reload(); });
        newTabButton.addActionListener(e -> openTab(homeUrl, true));

        navPanel.add(backButton);
        navPanel.add(forwardButton);
        navPanel.add(reloadButton);
        navPanel.add(newTabButton);

        // --- address bar with history dropdown ---
        // Built already-populated (from loadHistoryLines(), a plain file
        // read with no Swing side effects) via the Vector constructor,
        // rather than starting empty and mutating it with addElement() in
        // a loop afterward. The combo box's popup only ever sees ONE
        // model state - fully populated - from the moment it's first
        // attached, instead of a burst of individual add events landing
        // on an already-attached-but-not-yet-realized model (which is
        // where JComboBox's internal popup sizing has been unreliable in
        // practice - it kept showing only the first couple of entries
        // even though the model itself, verified via the Ctrl+P history
        // action, genuinely held everything).
        addressHistory = new DefaultComboBoxModel<>(new Vector<>(loadHistoryLines()));
        addressBar = new JComboBox<>(addressHistory);
        // The dropdown popup is lightweight by default - a Swing panel
        // painted inside this window, not a real OS window of its own.
        // That's a problem here specifically because the CEF browser view
        // filling most of this frame (see `client.createBrowser()` above)
        // is a HEAVYWEIGHT native component - a real embedded child
        // window. Heavyweight components always paint on top of
        // lightweight ones in the same window, regardless of Swing's own
        // z-order, so wherever the open popup overlaps the browser view
        // beneath it, the browser wins and the popup content underneath
        // never gets drawn - it looks like the list is short/empty past
        // whatever sliver doesn't overlap the browser, even though the
        // model itself (verified via Ctrl+P) is complete. Forcing the
        // popup to be heavyweight too makes it a real OS-level window,
        // which the platform z-orders correctly against the browser's
        // own native window instead of losing a same-window paint race.
        addressBar.setLightWeightPopupEnabled(false);
        addressBar.setEditable(true);
        addressBar.setFont(addressBar.getFont().deriveFont(Font.PLAIN));

        addressField = (JTextField) addressBar.getEditor().getEditorComponent();
        installEmacsKeyBindings(addressField);

        // The CEF windowed browser is a heavyweight native component, and
        // java-cef has a long-standing quirk (see java-cef issues #42 and
        // #297) where it can hang on to native keyboard focus even after
        // Swing's focus model has moved on. Just asking it to blur
        // (browser.setFocus(false)) isn't reliably enough to hand real OS
        // keyboard focus back to a lightweight sibling like this field —
        // everything LOOKS focused (caret blinks) but keystrokes never
        // arrive, until the whole window loses and regains native focus
        // (e.g. alt-tab away and back — which is exactly what forces the
        // native re-release that fixes it). clearGlobalFocusOwner() does
        // that same forced release programmatically, right before the
        // field's own click-to-focus handling runs and reclaims focus for
        // real. (Deliberately only on mousePressed, not focusGained — a
        // focusGained handler that itself clears/re-requests focus can
        // recurse into itself.)
        addressField.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (activeTab != null) activeTab.browser.setFocus(false);
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
            }
        });

        // Enter-in-editor and "pick an item from the dropdown history list"
        // are handled separately and deliberately do NOT share a single
        // JComboBox ActionListener. A combo box's generic action event
        // conflates several distinct internal firing paths (edit-commit,
        // popup selection, and — because recordVisit()/syncAddressBarToActiveTab()
        // below also call setSelectedItem() — our own programmatic
        // updates), and re-reading the editor's text reactively inside
        // that one handler is prone to picking up stale content from a
        // different firing than the one the user intended. That's what
        // caused an earlier bug where clicking a history entry would
        // briefly navigate there and then snap back to the previous page:
        // a second, stale firing of the same handler re-read the
        // *previous* URL and re-loaded it.
        //
        // Instead: the editor field's own Enter key is handled directly
        // (authoritative — it's this component's own current text), and
        // dropdown selection is handled via an ItemListener, which
        // receives the newly-selected value directly in the event rather
        // than requiring a re-query.
        addressField.addActionListener(e -> navigateTo(addressField.getText()));
        addressBar.addItemListener(e -> {
            if (updatingAddressBar) return;
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                Object item = e.getItem();
                if (item != null) navigateTo(item.toString());
            }
        });

        toolBar.add(navPanel, BorderLayout.WEST);
        toolBar.add(addressBar, BorderLayout.CENTER);
        return toolBar;
    }

    private JButton makeNavButton(String label, String tooltip) {
        JButton b = new JButton(label);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.setMargin(new Insets(2, 6, 2, 6));
        b.setPreferredSize(new Dimension(32, 26));
        return b;
    }

    /**
     * Read-only field along the bottom-left that echoes the URL of
     * whatever link the cursor is currently hovering over in the active
     * tab, and goes blank again once the cursor moves off the link.
     */
    private JTextField buildStatusBar() {
        statusBar = new JTextField();
        statusBar.setEditable(false);
        statusBar.setFocusable(false);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, java.awt.Color.GRAY),
                new EmptyBorder(2, 4, 2, 4)));
        return statusBar;
    }

    /**
     * Navigates the ACTIVE tab to the given URL, unless it's already the
     * page that tab has loaded (or is loading) — which also makes this
     * safely idempotent against a well-known JComboBox quirk where
     * committing an edit can fire both our field-level Enter handler AND
     * an implicit selection-commit ItemEvent for the same typed text.
     */
    private void navigateTo(String url) {
        if (url == null || activeTab == null) return;
        url = url.trim();
        if (url.isEmpty()) return;
        if (url.equals(activeTab.browser.getURL())) return;
        historyBrowseIndex = -1;
        activeTab.browser.loadURL(url);
    }

    /**
     * Adds url to the shared "recently visited" history dropdown, most
     * recent first, no duplicates — for ANY tab's navigation, active or
     * not, WITHOUT touching what's currently displayed in the visible
     * address bar (that's syncAddressBarToActiveTab()'s job, called
     * separately only when the navigating tab is the active one).
     *
     * The reorder runs entirely under the updatingAddressBar guard, not
     * just a final setSelectedItem() call. That matters because of a
     * DefaultComboBoxModel quirk: if the entry being removed by
     * removeElementAt() happens to be the model's CURRENTLY selected item
     * (i.e. whatever the address bar is showing right now, from a
     * possibly-different active tab), the model silently reassigns
     * selection to the *preceding* entry and fires a live, unguarded
     * ItemEvent for it — which our ItemListener would treat as a real
     * user pick and navigate the active tab to, entirely because some
     * OTHER, background tab happened to revisit an old URL. Guarding the
     * whole thing prevents that regardless of which tab triggered it.
     */
    private void recordVisit(String url) {
        if (url == null || url.isEmpty()) return;
        // homeUrl doesn't need to clutter the history dropdown - it's
        // always one click away via the home/new-tab button, so there's
        // no value in it also taking up a history slot (and every fresh
        // BrowserFrame visits it once at startup via openTab(), which
        // would otherwise bump it to the top of the list on every launch).
        if (url.equals(homeUrl)) return;
        updatingAddressBar = true;
        try {
            int existing = addressHistory.getIndexOf(url);
            if (existing >= 0) {
                addressHistory.removeElementAt(existing);
            }
            addressHistory.insertElementAt(url, 0);
            // Matches the old MemComboBox's MAX_MEM_LEN cap - drop the
            // oldest (last) entry once the list grows past it.
            while (addressHistory.getSize() > MAX_HISTORY_ENTRIES) {
                addressHistory.removeElementAt(addressHistory.getSize() - 1);
            }
        } finally {
            updatingAddressBar = false;
        }
        saveHistoryToFile();
    }

    /**
     * Populates the shared address history from historyFile, most-recent-
     * first (one URL per line, same format/order MemComboBox used to read
     * and write), so it survives across CirclePack sessions. Does nothing
     * if there's no history file configured, or it doesn't exist yet (a
     * brand new install/user - not an error).
     */
    private List<String> loadHistoryLines() {
        List<String> lines = new ArrayList<>();
        if (historyFile == null || !historyFile.exists()) return lines;
        try (BufferedReader reader = new BufferedReader(new FileReader(historyFile))) {
            String line;
            while (lines.size() < MAX_HISTORY_ENTRIES && (line = reader.readLine()) != null) {
                line = line.trim();
                // Also filters out homeUrl if it's already present from a
                // previous session (e.g. saved before this filtering was
                // added) - dropping it here means the very next save
                // rewrites the file without it too, so an old entry
                // self-heals away rather than lingering indefinitely.
                if (!line.isEmpty() && !line.equals(homeUrl)) {
                    lines.add(line);
                }
            }
        } catch (IOException e) {
            CirclePack.cpb.errMsg("Failed to load browser history from " + historyFile + ".");
        }
        return lines;
    }

    /**
     * Rewrites historyFile with the current, in-memory address history,
     * most-recent-first - mirrors the old MemComboBox.save(), which
     * likewise rewrote the whole file after every visit. historyFile's
     * parent directory is created on demand (mirrors the old
     * BrowserFrame's file.createNewFile() at construction) in case it
     * doesn't exist yet - e.g. a brand new preferences directory.
     */
    private void saveHistoryToFile() {
        if (historyFile == null) return;
        try {
            File parent = historyFile.getParentFile();
            if (parent != null) parent.mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(historyFile))) {
                for (int i = 0; i < addressHistory.getSize(); i++) {
                    writer.write(addressHistory.getElementAt(i));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            CirclePack.cpb.errMsg("Failed to save browser history to " + historyFile + ".");
        }
    }

    /** Makes the visible address bar show the active tab's current URL (guarded so this sync itself doesn't look like a user pick). */
    private void syncAddressBarToActiveTab() {
        if (activeTab == null) return;
        updatingAddressBar = true;
        try {
            addressBar.setSelectedItem(activeTab.browser.getURL());
        } finally {
            updatingAddressBar = false;
        }
        historyBrowseIndex = -1;
    }

    /**
     * Installs a classic Emacs / GNU-readline single-line editing keymap
     * on the address field, layered on top of the platform defaults
     * rather than replacing them (Home/End/arrow keys etc. still work).
     * Movement and simple deletion are wired to the field's own
     * DefaultEditorKit actions; kill/yank/transpose/history/abort are
     * small custom actions below, backed by a one-slot kill buffer (not
     * a full multi-entry kill ring). Note this deliberately overrides a
     * couple of platform conventions that collide with Emacs bindings —
     * e.g. Ctrl+A is "beginning of line" here, not "select all" — since
     * that collision is the whole point of asking for Emacs bindings.
     */
    private void installEmacsKeyBindings(JTextField field) {
        InputMap im = field.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = field.getActionMap();

        // Make sure concrete Action instances back the standard caret/
        // delete action names, regardless of what the current look-and-feel
        // happens to pre-install under those names.
        for (Action a : new DefaultEditorKit().getActions()) {
            Object name = a.getValue(Action.NAME);
            if (name != null && am.get(name) == null) {
                am.put(name, a);
            }
        }

        bind(im, KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK, DefaultEditorKit.forwardAction);
        bind(im, KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK, DefaultEditorKit.backwardAction);
        bind(im, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK, DefaultEditorKit.beginLineAction);
        bind(im, KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, DefaultEditorKit.endLineAction);
        bind(im, KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK, DefaultEditorKit.nextWordAction);
        bind(im, KeyEvent.VK_B, InputEvent.ALT_DOWN_MASK, DefaultEditorKit.previousWordAction);
        bind(im, KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, DefaultEditorKit.deleteNextCharAction);
        bind(im, KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK, DefaultEditorKit.deletePrevCharAction);

        am.put("emacs-kill-line", killLineAction(field));
        am.put("emacs-kill-to-bol", killToBeginningAction(field));
        am.put("emacs-kill-word-back", killWordBackAction(field));
        am.put("emacs-kill-word-fwd", killWordForwardAction(field));
        am.put("emacs-yank", yankAction(field));
        am.put("emacs-transpose", transposeCharsAction(field));
        am.put("emacs-history-prev", historyAction(field, 1));  // C-p: older
        am.put("emacs-history-next", historyAction(field, -1)); // C-n: newer
        am.put("emacs-abort", abortEditAction(field));

        bind(im, KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK, "emacs-kill-line");
        bind(im, KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK, "emacs-kill-to-bol");
        bind(im, KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK, "emacs-kill-word-back");
        bind(im, KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK, "emacs-kill-word-fwd");
        bind(im, KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, "emacs-yank");
        bind(im, KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK, "emacs-transpose");
        bind(im, KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK, "emacs-history-prev");
        bind(im, KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, "emacs-history-next");
        bind(im, KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK, "emacs-abort");
    }

    private static void bind(InputMap im, int keyCode, int modifiers, Object actionKey) {
        im.put(KeyStroke.getKeyStroke(keyCode, modifiers), actionKey);
    }

    /** Ctrl+K: delete from caret to end of line, saving the killed text. */
    private Action killLineAction(JTextField field) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int pos = field.getCaretPosition();
                String text = field.getText();
                if (pos < text.length()) {
                    killBuffer = text.substring(pos);
                    field.setText(text.substring(0, pos));
                    field.setCaretPosition(pos);
                }
            }
        };
    }

    /** Ctrl+U: delete from start of line to caret (readline unix-line-discard), saving the killed text. */
    private Action killToBeginningAction(JTextField field) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int pos = field.getCaretPosition();
                String text = field.getText();
                if (pos > 0) {
                    killBuffer = text.substring(0, pos);
                    field.setText(text.substring(pos));
                    field.setCaretPosition(0);
                }
            }
        };
    }

    /** Ctrl+W: delete the word before the caret, saving the killed text. */
    private Action killWordBackAction(JTextField field) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    int caret = field.getCaretPosition();
                    int start = Utilities.getPreviousWord(field, caret);
                    String text = field.getText();
                    killBuffer = text.substring(start, caret);
                    field.setText(text.substring(0, start) + text.substring(caret));
                    field.setCaretPosition(start);
                } catch (BadLocationException ex) {
                    // already at the start of the field — nothing to kill
                }
            }
        };
    }

    /** Alt+D: delete the word after the caret, saving the killed text. */
    private Action killWordForwardAction(JTextField field) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    int caret = field.getCaretPosition();
                    int end = Utilities.getNextWord(field, caret);
                    String text = field.getText();
                    killBuffer = text.substring(caret, end);
                    field.setText(text.substring(0, caret) + text.substring(end));
                    field.setCaretPosition(caret);
                } catch (BadLocationException ex) {
                    // already at the end of the field — nothing to kill
                }
            }
        };
    }

    /** Ctrl+Y: insert the last-killed text at the caret. */
    private Action yankAction(JTextField field) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (killBuffer.isEmpty()) return;
                int caret = field.getCaretPosition();
                String text = field.getText();
                field.setText(text.substring(0, caret) + killBuffer + text.substring(caret));
                field.setCaretPosition(caret + killBuffer.length());
            }
        };
    }

    /** Ctrl+T: swap the two characters before the caret (or the last two, at end of field). */
    private Action transposeCharsAction(JTextField field) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                String text = field.getText();
                int len = text.length();
                if (len < 2) return;
                int i = field.getCaretPosition();
                if (i == 0) i = 1;
                if (i >= len) i = len - 1;
                StringBuilder sb = new StringBuilder(text);
                char a = sb.charAt(i - 1);
                sb.setCharAt(i - 1, sb.charAt(i));
                sb.setCharAt(i, a);
                field.setText(sb.toString());
                field.setCaretPosition(Math.min(i + 1, len));
            }
        };
    }

    /**
     * Ctrl+P / Ctrl+N: readline/Emacs-minibuffer-style history recall —
     * cycles the address field's text through the shared history dropdown
     * without navigating or disturbing the combo box's own selection.
     * direction: +1 moves toward older entries, -1 toward newer ones.
     */
    private Action historyAction(JTextField field, int direction) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int size = addressHistory.getSize();
                if (size == 0) return;
                int next = historyBrowseIndex + direction;
                if (next < 0 || next >= size) return;
                historyBrowseIndex = next;
                field.setText(addressHistory.getElementAt(historyBrowseIndex));
                field.setCaretPosition(field.getText().length());
            }
        };
    }

    /** Ctrl+G: abandon in-progress edits and revert to the active tab's currently loaded URL. */
    private Action abortEditAction(JTextField field) {
        return new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                String current = activeTab != null ? activeTab.browser.getURL() : null;
                field.setText(current == null ? "" : current);
                field.setCaretPosition(field.getText().length());
                historyBrowseIndex = -1;
            }
        };
    }

    /**
     * Tracks page titles, addresses, and link-hover status messages for
     * EVERY tab (registered once on the shared client). Each callback
     * hands back the specific CefBrowser it's for; tabFor() maps that
     * back to the owning tab so its own tab-strip label can always be
     * kept current, while the shared toolbar/status bar/window title are
     * only touched when the event is for the currently ACTIVE tab.
     */
    private class BrowserDisplayHandler extends CefDisplayHandlerAdapter {
        @Override
        public void onTitleChange(CefBrowser browser, String title) {
            SwingUtilities.invokeLater(() -> {
                BrowserTab tab = tabFor(browser);
                if (tab == null) return;
                tab.title = (title == null || title.isEmpty()) ? "New Tab" : title;
                if (tab.headerLabel != null) {
                    tab.headerLabel.setText(shortenTitle(tab.title));
                }
                if (tab == activeTab) {
                    setTitle(tab.title.isEmpty() ? DEFAULT_TITLE : tab.title + " - " + DEFAULT_TITLE);
                }
            });
        }

        @Override
        public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
            // CEF fires this for EVERY frame's address, not just the top-level
            // page - any iframe on the page (ads, embedded widgets, trackers,
            // etc.) generates its own onAddressChange with its own URL. A
            // single page load can easily fire this a dozen+ times.
            // Recording all of them would flood the shared "recently
            // visited"/persisted history with sub-resource URLs nobody
            // navigated to, evicting real entries once the MAX_HISTORY_ENTRIES
            // cap is hit - only the main frame's address is an actual
            // page visit worth remembering or persisting.
            if (frame != null && !frame.isMain()) return;
            SwingUtilities.invokeLater(() -> {
                recordVisit(url); // any tab's navigation updates the shared history list
                BrowserTab tab = tabFor(browser);
                if (tab != null && tab == activeTab) {
                    syncAddressBarToActiveTab(); // only the active tab's navigation should move the visible address bar
                }
            });
        }

        @Override
        public void onStatusMessage(CefBrowser browser, String value) {
            // CEF sends the hovered link's URL here, and an empty string
            // once the cursor moves off the link. Only the active tab's
            // hover state is relevant to what's shown.
            SwingUtilities.invokeLater(() -> {
                BrowserTab tab = tabFor(browser);
                if (tab == null || tab != activeTab) return;
                statusBar.setText(value == null ? "" : value);
            });
        }
    }

    /** Keeps the back/forward/reload buttons in sync with the ACTIVE tab's real browser history — every tab's own load state is tracked internally by its CefBrowser regardless. */
    private class BrowserLoadHandler extends CefLoadHandlerAdapter {
        @Override
        public void onLoadingStateChange(CefBrowser browser, boolean isLoading,
                boolean canGoBack, boolean canGoForward) {
            SwingUtilities.invokeLater(() -> {
                BrowserTab tab = tabFor(browser);
                if (tab == null || tab != activeTab) return;
                backButton.setEnabled(canGoBack);
                forwardButton.setEnabled(canGoForward);
                reloadButton.setEnabled(!isLoading);
                if (!isLoading) reclaimAddressBarFocusIfNeeded();
            });
        }
    }

    /**
     * Finishing a page load is another spot where java-cef's native browser
     * re-asserts OS-level keyboard focus (same underlying quirk as the
     * mousePressed handler above deals with — java-cef issues #42/#297),
     * even when the address bar was the field the user was actually working
     * in. Swing itself never got told focus moved, so the address field
     * still LOOKS focused (caret blinking, isFocusOwner() true) but
     * keystrokes silently stop arriving until the same clear-and-reclaim
     * dance runs again. Only do this when the address field genuinely still
     * thinks it owns focus — a load finishing while the user has clicked
     * into the page itself should NOT yank focus back to the address bar.
     */
    private void reclaimAddressBarFocusIfNeeded() {
        if (!addressField.isFocusOwner()) return;
        if (activeTab != null) activeTab.browser.setFocus(false);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
        SwingUtilities.invokeLater(addressField::requestFocusInWindow);
    }

    /**
     * Intercepts navigation — in ANY tab — to CirclePack script files
     * (.cps/.xmd/.cmd) and packing files (.p/.q). Instead of letting CEF
     * try to display them, the navigation is cancelled and the URL is
     * handed off to CirclePack's own script/packing loading machinery,
     * mirroring the extension dispatch in the old JEditorPane-based
     * BrowserFrame's load(String) method. Registered once on the shared
     * client (like the display/load handlers above), so it applies to
     * every tab automatically, including tabs opened later.
     */
    private class ScriptPackingRequestHandler extends CefRequestHandlerAdapter {
        @Override
        public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request,
                boolean user_gesture, boolean is_redirect) {
            String url = request.getURL();
            if (url == null) return false;
            String path = pathOnly(url).toLowerCase();

            if (endsWithAny(path, SCRIPT_EXTENSIONS)) {
                handleScript(url);
                return true; // cancel — CirclePack handles it instead of CEF
            }
            if (endsWithAny(path, PACKING_EXTENSIONS)) {
                handlePacking(url);
                return true;
            }
            return false; // not a script/packing URL — let CEF navigate normally
        }
    }

    /** The URL's path, with any query string or fragment stripped off — same slice the old code got from URL.getFile(). */
    private static String pathOnly(String url) {
        int end = url.length();
        int q = url.indexOf('?');
        if (q >= 0 && q < end) end = q;
        int h = url.indexOf('#');
        if (h >= 0 && h < end) end = h;
        return url.substring(0, end);
    }

    private static boolean endsWithAny(String lowerCasePath, String[] extensions) {
        for (String ext : extensions) {
            if (lowerCasePath.endsWith(ext)) return true;
        }
        return false;
    }

    private static boolean isLocalFileUrl(String url) {
        return url.toLowerCase().startsWith("file:");
    }

    /**
     * Converts a file: URL to a local filesystem path, handling percent-
     * encoding (e.g. "%20" for spaces) via URI rather than the old code's
     * manual string surgery. Falls back to simple slash-stripping for a
     * malformed file: URL that the URI/URL constructors reject outright.
     */
    private static String localPathFromFileUrl(String url) {
        try {
            return new File(new URL(url).toURI()).getPath();
        } catch (Exception e) {
            String path = url.substring("file:".length());
            while (path.startsWith("/")) path = path.substring(1);
            return "/" + path;
        }
    }

    /**
     * Handles a click on a .cps/.xmd/.cmd script URL. Local files are
     * loaded immediately (right on the EDT, since onBeforeBrowse already
     * runs off it and Swing/CirclePack calls must be marshalled back on).
     * Remote files are downloaded to a temporary local file on a
     * background thread first — CirclePack's script loader only knows how
     * to read local paths — and only then handed off on the EDT, exactly
     * as the old threaded-download approach did to respect the Swing
     * Single Thread Rule.
     */
    private void handleScript(final String url) {
        if (isLocalFileUrl(url)) {
            String path = localPathFromFileUrl(url);
            SwingUtilities.invokeLater(() -> loadScriptFile(path, path));
            return;
        }
        new Thread(() -> {
            File tempFile;
            try {
                tempFile = downloadTemporaryFile(new URL(url));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> CirclePack.cpb.errMsg("Could not download script file " + url + "."));
                return;
            }
            final String tempPath = tempFile.getPath();
            SwingUtilities.invokeLater(() -> loadScriptFile(tempPath, url));
        }).start();
    }

    /** Loads a script already sitting at a local path (readPath) into CirclePack, labelling it with labelUrl (the true, possibly-remote, location). Must run on the EDT. */
    private void loadScriptFile(String readPath, String labelUrl) {
        boolean newScript = CPBase.scriptManager.getScript(readPath, labelUrl, true) > 0;
        if (newScript) {
            PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new java.awt.Point(0, 0));
        } else {
            CirclePack.cpb.errMsg("Failed to load script " + labelUrl + ".");
        }
    }

    /**
     * Handles a click on a .p/.q packing URL: confirms with the user (as
     * the old code did — packing loads overwrite the active pack), then
     * loads it, downloading remote files to a temporary local file first
     * on a background thread. The confirm dialog and everything after it
     * run on the EDT throughout.
     */
    private void handlePacking(final String url) {
        SwingUtilities.invokeLater(() -> {
            String prompt = "Load into pack " + CirclePack.cpb.getActivePackData().packNum + "?";
            int result = JOptionPane.showConfirmDialog(this, prompt, "Confirm", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) return;

            if (isLocalFileUrl(url)) {
                String path = localPathFromFileUrl(url);
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(path));
                    readPackingFile(reader, path);
                } catch (FileNotFoundException e) {
                    CirclePack.cpb.errMsg("Failed to open " + url + ".");
                }
                return;
            }

            new Thread(() -> {
                File tempFile;
                try {
                    tempFile = downloadTemporaryFile(new URL(url));
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> CirclePack.cpb.errMsg("Could not download packing file " + url + "."));
                    return;
                }
                final BufferedReader reader;
                try {
                    reader = new BufferedReader(new FileReader(tempFile));
                } catch (FileNotFoundException e) {
                    SwingUtilities.invokeLater(() -> CirclePack.cpb.errMsg("Failed to open " + url + "."));
                    return;
                }
                final String tempPath = tempFile.getPath();
                SwingUtilities.invokeLater(() -> readPackingFile(reader, tempPath));
            }).start();
        });
    }

    /** Reads packing data from reader into the active pack and refreshes the display, mirroring the old load()'s .p/.q handling. Must run on the EDT. path is only consulted for its .p/.q extension (to decide whether to cleanse first) and as a label passed through to readpack(). */
    private void readPackingFile(BufferedReader reader, String path) {
        if (path.toLowerCase().endsWith(".p")) {
            TrafficCenter.cmdGUI("cleanse");
        }
        PackData tmppd = CirclePack.cpb.getActivePackData();
        ReadWrite.readpack(reader, tmppd, path);
        if (tmppd.getDispOptions != null) {
            CommandStrParser.jexecute(tmppd, "disp -wr");
        } else {
            TrafficCenter.cmdGUI("disp -w -c");
        }
    }

    /**
     * Downloads target to a uniquely-named local temporary file (deleted
     * on JVM exit) and returns it. Blocks until the download completes —
     * callers must run this off the Swing EDT (see handleScript/
     * handlePacking above), per the Swing Single Thread Rule. Ported from
     * the old BrowserUtilities.downloadTemporaryFile(URL).
     */
    private static File downloadTemporaryFile(URL target) throws IOException {
        String targetName = new File(target.getPath()).getName();

        // Get a unique temporary file and use its name to create a unique
        // temporary directory instead, so the download can be saved under
        // its original file name inside that directory.
        File temporaryDirectory = File.createTempFile(targetName, "");
        if (!temporaryDirectory.delete()) {
            throw new IOException("Failed to delete temporary file " + temporaryDirectory
                    + " in preparation for temporary directory creation!");
        }
        if (!temporaryDirectory.mkdir()) {
            throw new IOException("Failed to create temporary directory " + temporaryDirectory + "!");
        }

        File temporaryFile = new File(temporaryDirectory, targetName);
        temporaryDirectory.deleteOnExit();
        temporaryFile.deleteOnExit();

        try (ReadableByteChannel rbc = Channels.newChannel(target.openStream());
                FileOutputStream fos = new FileOutputStream(temporaryFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }

        return temporaryFile;
    }

    /**
     * Permanently releases every tab's CEF browser plus this window's
     * shared client, and disposes the Swing window itself. Safe to call
     * more than once. This is irreversible — browser.close()/
     * client.dispose() cannot be undone, so once this runs the frame can
     * no longer be shown again. Ordinary window-closing (the X button, or
     * closing the last remaining tab) does NOT call this — both just hide
     * the frame (see the HIDE_ON_CLOSE comment in the constructor and
     * closeTab()'s javadoc) so the same browser can be reopened later.
     * Call closeBrowser() explicitly instead: from CirclePack's
     * exit()/shutdown path for every open BrowserFrame BEFORE that path
     * calls CefAppHolder.shutdown(), or from any action meant to discard
     * this particular browser window for good.
     */
    public synchronized void closeBrowser() {
        if (browserClosed) {
            return;
        }
        browserClosed = true;
        for (BrowserTab tab : new ArrayList<>(openTabs)) {
            tab.browser.close(true);
        }
        openTabs.clear();
        client.dispose();
        dispose(); // JFrame.dispose() — the underlying browsers can never
                   // come back, so there's no reason to keep the window around
    }
}
