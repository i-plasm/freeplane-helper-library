package io.github.iplasm.app.freeplane.library;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JOptionPane;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.Compat;
import org.freeplane.core.util.Hyperlink;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.ui.IMapViewManager;
import org.freeplane.features.url.FreeplaneUriConverter;
import org.freeplane.features.url.NodeAndMapReference;
import org.freeplane.features.url.UrlManager;
import org.freeplane.main.application.Browser;
import org.freeplane.n3.nanoxml.XMLException;
import org.freeplane.n3.nanoxml.XMLParseException;
import io.github.iplasm.library.jhelperutils.IOUtils;
import io.github.iplasm.library.jhelperutils.TextUtils;

/**
 * 
 * This class depends on the external lib:
 * 
 * java-commons (Github: https://github.com/i-plasm/jhelperutils/)
 * 
 */
public class FreeplaneIOHelper {

  public static void loadMindMap(URI uri) throws FileNotFoundException, XMLParseException,
      IOException, URISyntaxException, XMLException {
    FreeplaneUriConverter freeplaneUriConverter = new FreeplaneUriConverter();
    final URL url = freeplaneUriConverter.freeplaneUrl(uri);
    final ModeController modeController = Controller.getCurrentModeController();
    modeController.getMapController().openMap(url);
  }

  public static boolean isFreeplaneAltBrowserMethodAvailable() {
    Class<?> browserClazz = null;
    try {
      browserClazz = Class.forName("org.freeplane.main.application.Browser");
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    Method method = null;
    try {
      method = browserClazz.getDeclaredMethod("openDocumentNotSupportedByDesktop", Hyperlink.class);
    } catch (NoSuchMethodException | SecurityException e) {
      // TODO Auto-generated catch block
      return false;
    }
    return true;
    // method.setAccessible(true);
  }

  private static void refl_useFreeplaneAlternativeBrowser(URI uri)
      throws ClassNotFoundException, NoSuchMethodException, SecurityException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    Class<?> browserClazz = Class.forName("org.freeplane.main.application.Browser");
    Method method =
        browserClazz.getDeclaredMethod("openDocumentNotSupportedByDesktop", Hyperlink.class);
    method.setAccessible(true);
    method.invoke(new Browser(), new Hyperlink(uri));
  }

  private static void refl_loadNodeReferenceURI(NodeAndMapReference nodeAndMapReference)
      throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
      NoSuchMethodException, SecurityException, ClassNotFoundException {
    Class<?> clazz = Class.forName("org.freeplane.features.url.UrlManager");
    Method method = clazz.getDeclaredMethod("loadNodeReferenceURI", NodeAndMapReference.class);
    method.setAccessible(true);
    method.invoke(new UrlManager(), nodeAndMapReference);
  }

  public static void openResourceUsingFreeplaneBroswer(URI uri) throws IOException {
    if (uri != null && uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("file")
        && !new File(uri).exists()) {
      throw new IOException("The file resource does not seem to exist.");
    }
    if (uri != null) {
      new Browser().openDocument(new Hyperlink(uri));
    } else {
      throw new IllegalArgumentException();
    }
  }

  public static void attemptToOpenResourceWithFreeplaneAlternativeBrowser(String urlOrPath, URI uri,
      URI uriForPossibleRelativePath)
      throws ClassNotFoundException, NoSuchMethodException, SecurityException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    try {
      refl_useFreeplaneAlternativeBrowser(uri);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
        | IllegalAccessException | IllegalArgumentException | InvocationTargetException e11) {
      if (uriForPossibleRelativePath != null) {
        refl_useFreeplaneAlternativeBrowser(uriForPossibleRelativePath);
      } else {
        throw new IllegalArgumentException();
      }
    }
  }

  public static void visitWebsite(String urlStr, String feedbackMessage) {
    URL url = null;
    try {
      url = new URL(urlStr);
    } catch (MalformedURLException e2) {
      // TODO Auto-generated catch block
      e2.printStackTrace();
    }
    URI uri = null;
    try {
      uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(),
          url.getPath(), url.getQuery(), url.getRef());
      uri = new URI(uri.toASCIIString());
    } catch (URISyntaxException e2) {
      // TODO Auto-generated catch block
      e2.printStackTrace();
    }

    openResource(uri.toString(), feedbackMessage);
  }

  public static void openResource(String urlOrPath, String feedbackMessage) {
    // Case: resource has script extension (.sh, .bat, , etc). These are not supported.
    final Set<String> execExtensions = new HashSet<String>(
        Arrays.asList(new String[] {"exe", "bat", "cmd", "sh", "command", "app"}));
    String extension = TextUtils.extractExtension(urlOrPath);
    if (execExtensions.contains(extension)) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It seems you are attempting to launch an executable or script file with extension '." +
              extension + "'" + " This service is not adequate for that purpose.");
      return;
    }

    // Case: map local link to node
    if (FreeplaneUtils.isNodeID(urlOrPath)) {
      if (!urlOrPath.startsWith("#")) {
        urlOrPath = "#" + urlOrPath;
      }
      FreeplaneUtils.goToNodeById(urlOrPath.substring(1));
      return;
    }

    // Case: especial uri: link to menu item
    if (urlOrPath.startsWith("menuitem:_")) {
      FreeplaneUtils.executeMenuItem(
          urlOrPath.substring(urlOrPath.indexOf("menuitem:_") + "menuitem:_".length()));
      return;
    }

    // Validating map has been saved
    File mapFile = FreeplaneUtils.getMapFile();
    if (mapFile == null) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          "It seems the currently focused map has never been saved. Please save it in order to use this service.");
      return;
    }
    File mapDir = mapFile.getParentFile();

    // Ajustment for windows
    if (Compat.isWindowsOS() && urlOrPath.toLowerCase().startsWith("c:/")) {
      urlOrPath = "file:///" + urlOrPath;
    }

    // When resource url begins with 'file:' scheme. Adjusting slashes for compatibility with most
    // OS
    if (urlOrPath.indexOf("file:/") == 0) {
      int numOfSlashes = 1;
      if (urlOrPath.startsWith("file:////")) {
        numOfSlashes = 4;
      } else if (urlOrPath.startsWith("file:///")) {
        numOfSlashes = 3;
      } else if (urlOrPath.startsWith("file://")) {
        numOfSlashes = 2;
      }

      urlOrPath =
          "file:///" + urlOrPath.substring("file:".length() + numOfSlashes, urlOrPath.length());
    }

    // Constructing the options for uri
    URI uri = null;
    URI uriForPossibleRelativePath =
        new Hyperlink(tryToResolveAndNormalizeRelativePath(urlOrPath, mapDir)).getUri();
    try {
      uri = atttemptToGetValidURI(urlOrPath, mapDir, uri);
    } catch (URISyntaxException e2) {
      JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
          feedbackMessage + System.lineSeparator() + System.lineSeparator() + urlOrPath
              + System.lineSeparator() + System.lineSeparator() + e2.getMessage());
      return;
    }

    // Case: mindmap, without node reference
    if (extension.equalsIgnoreCase("mm")) {
      try {
        loadMindMap(uri);
      } catch (IOException | URISyntaxException | XMLException | RuntimeException e) {
        try {
          loadMindMap(uriForPossibleRelativePath);
        } catch (IOException | URISyntaxException | XMLException | RuntimeException e1) {
          JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
              "External MindMap could not be loaded. It either does not exist or could not be resolved." +
                  System.lineSeparator() + System.lineSeparator() + urlOrPath +
                  System.lineSeparator() + "Message: " + e1.getMessage());
        }
      }
      return;
    }

    // Case: mindmap, with node reference
    final NodeAndMapReference nodeAndMapReference = new NodeAndMapReference(urlOrPath);
    if (nodeAndMapReference.hasNodeReference()) {
      if (uriForPossibleRelativePath != null) {
        urlManagerLoadHyperlink(uriForPossibleRelativePath);
      } else {
        urlManagerLoadHyperlink(uri);
      }
      return;
    }

    // General case (i.e, not a node or a Freeplane mindmap)
    URI defaultURI = uri;
    String userInput = urlOrPath;
    // Thread for opening the resource
    Thread thread = new Thread(new Runnable() {
      URI uri = defaultURI;
      String urlOrPath = userInput;

      @Override
      public void run() {

        if (uri == null && uriForPossibleRelativePath == null) {
          showResourceCouldNotBeOpenPrompt(null);
        }

        // Case: when desktop is supported and the OS is either Windows or Mac
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
            && (Compat.isMacOsX() || Compat.isWindowsOS())) {
          try {
            IOUtils.browseURLOrPathViaDesktop(uri);
          } catch (IOException e) {

            if (uriForPossibleRelativePath != null) {

              try {
                IOUtils.browseURLOrPathViaDesktop(uriForPossibleRelativePath);
              } catch (IOException e1) {
                // uriForPossibleRelativePath: Now attempting with alternative methods for browsing
                // resources
                if (Compat.isWindowsOS()) {
                  try {
                    openResourceUsingFreeplaneBroswer(uriForPossibleRelativePath);
                  } catch (IOException e2) {
                    showResourceCouldNotBeOpenPrompt(e2);
                  }
                } else {
                  alternativeOpen(uriForPossibleRelativePath);
                }
              }

            } else {
              // uri: Now attempting with alternative methods for browsing resources
              if (Compat.isWindowsOS()) {
                try {
                  openResourceUsingFreeplaneBroswer(uri);
                } catch (IOException e1) {
                  showResourceCouldNotBeOpenPrompt(e1);
                }
              } else {
                alternativeOpen(uri);
              }
            }

          }
        }
        // Case: when desktop is not supported and/or the OS is neither Windows nor Mac
        else {
          // Pre-check in case the resource is a file that doesn't exist, since the
          // command below may not throw an exception that we could use to inform the user
          if (uriForPossibleRelativePath != null
              && uriForPossibleRelativePath.getScheme().equalsIgnoreCase("file")
              && !new File(uriForPossibleRelativePath).exists()) {
            showResourceCouldNotBeOpenPrompt(null);
            // Intentionally not returning here, to try a very last time in case the method
            // 'File...exists()' did not give an accurate result
          }

          URI uriToTry = uriForPossibleRelativePath == null ? uri : uriForPossibleRelativePath;
          alternativeOpen(uriToTry);
        }
      }

      void showResourceCouldNotBeOpenPrompt(Exception e) {
        String exceptionMsg = e == null ? "" : e.getMessage();
        JOptionPane.showMessageDialog(UITools.getCurrentFrame(),
            feedbackMessage + System.lineSeparator() + System.lineSeparator() + urlOrPath
                + System.lineSeparator() + System.lineSeparator() + exceptionMsg);
      }

      void alternativeOpen(URI uriToTry) {
        try {
          if (!Compat.isMacOsX() && !Compat.isWindowsOS()) {
            altOpenOtherOS(uriToTry, false);
          } else if (Compat.isMacOsX()) {
            altOpenMacOS(uriToTry, false);
          }
        } catch (IOException e) {
          showResourceCouldNotBeOpenPrompt(e);
        }
      }

    });
    thread.setName("FreeplaneUtils: " + "OPEN_RESOURCE");
    thread.start();
  }

  public static URI atttemptToGetValidURI(String urlOrPath, File mapDir, URI uri)
      throws URISyntaxException {
    // First uri attempt
    try {
      uri = LinkController.createHyperlink(urlOrPath).getUri();
    } catch (URISyntaxException e) {
      // Second uri attempt
      URL url;
      try {
        url = new URL(urlOrPath);
        try {
          uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(),
              url.getRef());
        } catch (URISyntaxException e1) {
          // Third uri attempt
          url = new Hyperlink(tryToResolveAndNormalizeRelativePath(urlOrPath, mapDir)).toUrl();
          uri = new URI(url.getProtocol(), url.getHost(), url.getPath(), url.getQuery(),
              url.getRef());
          // e1.printStackTrace();
        }
      } catch (MalformedURLException e2) {
        // e2.printStackTrace();
      }
      // uri = new Hyperlink(new File(urlOrPath).toURI().normalize()).getUri();
      // e.printStackTrace();
    }
    return uri;
  }

  public static URI tryToResolveAndNormalizeRelativePath(String url, File baseDir) {
    URI uri = null;
    try {
      uri = new URL(baseDir.toURL(), url).toURI().normalize();
    } catch (Exception e) {
      try {
        uri = new URL(baseDir.toURL(), LinkController.createHyperlink(url).getUri().toString())
            .toURI().normalize();
      } catch (Exception e1) {
      }
    }
    return uri;
  }

  /**
   * @throws RuntimeException (propagated from Freeplanes attempt to load the hyperlink)
   */
  public static void urlManagerLoadHyperlink(URI uri) {
    UrlManager urlManager = Controller.getCurrentModeController().getExtension(UrlManager.class);
    try {
      urlManager.loadHyperlink(new Hyperlink(uri));
    } catch (RuntimeException e) {
      throw new RuntimeException(e.getMessage());
    }
  }


  public static void altOpenOtherOS(URI uriToTry, boolean shouldWaitFor) throws IOException {
    try {
      IOUtils.openViaOSCommand(uriToTry.toString(),
          FreeplaneUtils.getProperty("default_browser_command_other_os"), shouldWaitFor);
    } catch (IOException e) {
      System.err.println("Caught: " + e);
      throw new IOException(e);
    }
  }

  public static void altOpenMacOS(URI uriToTry, boolean shouldWaitFor) throws IOException {
    String uriString = uriToTry.toString();
    if (uriToTry.getScheme().equals("file")) {
      uriString = uriToTry.getPath().toString();
    }

    try {
      IOUtils.openViaOSCommand(uriString, FreeplaneUtils.getProperty("default_browser_command_mac"),
          shouldWaitFor);
    } catch (IOException e) {
      System.err.println("Caught: " + e);
      throw new IOException(e);
    }
  }

  public static String getURIForNode(NodeModel nodeModel) {
    String fileBasedUri =
        nodeModel.getMap().getFile().toURI().toString() + '#' + nodeModel.createID();
    FreeplaneUriConverter freeplaneUriConverter = new FreeplaneUriConverter();
    return freeplaneUriConverter.freeplaneUriForFile(fileBasedUri);
  }

  public static String getURIForCurrentMap() {
    String fileBasedUri =
        FreeplaneUtils.getSelectedNodeModel().getMap().getFile().toURI().toString();
    FreeplaneUriConverter freeplaneUriConverter = new FreeplaneUriConverter();
    return freeplaneUriConverter.freeplaneUriForFile(fileBasedUri);
  }

  public static boolean tryToChangeToMapIfOpened(URI uri) throws MalformedURLException {
    FreeplaneUriConverter freeplaneUriConverter = new FreeplaneUriConverter();
    final URL url = freeplaneUriConverter.freeplaneUrl(uri);
    IMapViewManager mapViewManager = Controller.getCurrentController().getMapViewManager();
    return mapViewManager.tryToChangeToMapView(url);
  }


}
