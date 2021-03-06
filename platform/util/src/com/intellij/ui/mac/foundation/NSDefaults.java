// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.foundation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NSDefaults {
  private static final Logger LOG = Logger.getInstance(NSDefaults.class);

  private static final Map<Integer, Color> ourAccentColId2Color;
  private static final Color ourDefaultAccentColor = new Color(46, 125, 246);   // blue
  private static final Color ourDefaultHighlightColor = new Color(185, 215, 251); // blue

  // NOTE: skip call of Foundation.invoke(myDefaults, "synchronize") (when read settings)
  // It waits for any pending asynchronous updates to the defaults database and returns; this method is unnecessary and shouldn't be used.

  public static final String ourTouchBarDomain = "com.apple.touchbar.agent";
  public static final String ourTouchBarNode = "PresentationModePerApp";
  public static final String ourTouchBarShowFnValue = "functionKeys";

  static {
    ourAccentColId2Color = new HashMap<Integer, Color>();
    ourAccentColId2Color.put(5, new Color(138, 69, 146));   // purple
    ourAccentColId2Color.put(6, new Color(229, 94, 156));   // pink
    ourAccentColId2Color.put(0, new Color(207, 71, 69));    // red
    ourAccentColId2Color.put(1, new Color(232, 135, 58));   // orange
    ourAccentColId2Color.put(2, new Color(243, 185, 75));   // yellow
    ourAccentColId2Color.put(3, new Color(120, 183, 86));   // green
    ourAccentColId2Color.put(-1, new Color(152, 152, 152)); // graphite
  }

  private static class Path {
    private final @NotNull ArrayList<Node> myPath = new ArrayList<Node>();

    @Override
    public String toString() {
      String res = "";
      for (Node pn: myPath) {
        if (!res.isEmpty()) res += " | ";
        res += pn.toString();
      }
      return res;
    }

    String readStringVal(@NotNull String key) { return readStringVal(key, false); }

    String readStringVal(@NotNull String key, boolean doSyncronize) {
      if (myPath.isEmpty())
        return null;

      final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
      try {
        final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
        if (defaults == null || defaults.equals(ID.NIL))
          return null;

        if (doSyncronize) {
          // NOTE: AppleDoc proposes to skip call of Foundation.invoke(myDefaults, "synchronize") - "this method is unnecessary and shouldn't be used."
          Foundation.invoke(defaults, "synchronize");
        }

        _readPath(defaults);
        final Node tail = myPath.get(myPath.size() - 1);
        if (!tail.isValid())
          return null;

        final ID valObj = Foundation.invoke(tail.cachedNodeObj, "objectForKey:", Foundation.nsString(key));
        if (valObj == null || valObj.equals(ID.NIL))
          return null;
        return Foundation.toStringViaUTF8(valObj);
      } finally {
        pool.drain();
        _resetPathCache();
      }
    }

    void writeStringValue(@NotNull String key, String val) {
      if (myPath.isEmpty())
        return;

      final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
      try {
        final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
        if (defaults == null || defaults.equals(ID.NIL))
          return;

        _readPath(defaults);

        int pos = myPath.size() - 1;
        Node child = myPath.get(pos--);
        if (!child.isValid()) {
          if (val == null) // nothing to erase
            return;
        }

        child.writeStringValue(key, val);
        while (pos >= 0) {
          final Node parent = myPath.get(pos--);
          final ID mnode = Foundation.invoke(parent.cachedNodeObj, "mutableCopy");
          Foundation.invoke(mnode, "setObject:forKey:", child.cachedNodeObj, Foundation.nsString(child.myNodeName));
          parent.cachedNodeObj = mnode;
          child = parent;
        }

        final String topWriteSelector = child.isDomain() ? "setPersistentDomain:forName:" : "setObject:forKey:";
        Foundation.invoke(defaults, topWriteSelector, child.cachedNodeObj, Foundation.nsString(child.myNodeName));
      } finally {
        pool.drain();
        _resetPathCache();
      }
    }

    int lastValidPos() {
      if (myPath.isEmpty())
        return -1;

      final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
      try {
        final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
        if (defaults == null || defaults.equals(ID.NIL))
          return -1;

        _readPath(defaults);

        for (int pos = 0; pos < myPath.size(); ++pos) {
          final Node pn = myPath.get(pos);
          if (!pn.isValid())
            return pos - 1;
        }
        return myPath.size() - 1;
      } finally {
        pool.drain();
        _resetPathCache();
      }
    }

    static @NotNull Path createDomainPath(@NotNull String domain, @NotNull String[] nodes) {
      final Path result = new Path();
      result.myPath.add(new Node("persistentDomainForName:", domain));
      for (String nodeName: nodes)
        result.myPath.add(new Node("objectForKey:", nodeName));
      return result;
    }

    static @NotNull Path createDomainPath(@NotNull String domain, @NotNull String nodeName) {
      final Path result = new Path();
      result.myPath.add(new Node("persistentDomainForName:", domain));
      result.myPath.add(new Node("objectForKey:", nodeName));
      return result;
    }

    private static class Node {
      private final @NotNull String mySelector;
      private final @NotNull String myNodeName;
      private @NotNull ID cachedNodeObj = ID.NIL;

      Node(@NotNull String selector, @NotNull String nodeName) {
        mySelector = selector;
        myNodeName = nodeName;
      }

      @Override
      public String toString() { return String.format("sel='%s' nodeName='%s'",mySelector, myNodeName); }

      boolean isValid() { return !cachedNodeObj.equals(ID.NIL); }

      boolean isDomain() { return mySelector.equals("persistentDomainForName:"); }

      void readNode(ID parent) {
        cachedNodeObj = ID.NIL;

        if (parent == null || parent.equals(ID.NIL))
          return;

        final ID nodeObj = Foundation.invoke(parent, mySelector, Foundation.nsString(myNodeName));
        if (nodeObj == null || nodeObj.equals(ID.NIL))
          return;

        cachedNodeObj = nodeObj;
      }

      private static ID _createDictionary() { return Foundation.invoke("NSMutableDictionary", "new"); }

      void writeStringValue(@NotNull String key, String val) {
        final ID mnode;
        if (!isValid()) {
          if (val == null) // nothing to erase
            return;

          mnode = _createDictionary();
        } else
          mnode = Foundation.invoke(cachedNodeObj, "mutableCopy");

        if (mnode == null || mnode.equals(ID.NIL))
          return;

        if (val != null)
          Foundation.invoke(mnode, "setObject:forKey:", Foundation.nsString(val), Foundation.nsString(key));
        else
          Foundation.invoke(mnode, "removeObjectForKey:", Foundation.nsString(key));

        cachedNodeObj = mnode;
      }
    }

    private void  _readPath(ID parent) {
      if (myPath.isEmpty())
        return;

      for (Node pn: myPath) {
        pn.readNode(parent);
        if (!pn.isValid())
          return;
        parent = pn.cachedNodeObj;
      }
    }
    private void  _resetPathCache() {
      for (Node pn: myPath)
        pn.cachedNodeObj = ID.NIL;
    }
  }

  public static boolean isShowFnKeysEnabled(String appId) {
    final Path path = Path.createDomainPath(ourTouchBarDomain, ourTouchBarNode);
    final String sval = path.readStringVal(appId);
    return sval != null && sval.equals(ourTouchBarShowFnValue);
  }

  /**
   * @return True when value has been changed
   */
  public static boolean setShowFnKeysEnabled(String appId, boolean val) { return setShowFnKeysEnabled(appId, val, false); }

  public static boolean setShowFnKeysEnabled(String appId, boolean val, boolean performExtraDebugChecks) {
    final Path path = Path.createDomainPath(ourTouchBarDomain, ourTouchBarNode);
    String sval = path.readStringVal(appId);
    final boolean settingEnabled = sval != null && sval.equals(ourTouchBarShowFnValue);

    final String initDesc = "appId='" + appId
                            + "', value (requested be set) ='" + val
                            + "', initial path (tail) value = '" + sval
                            + "', path='" + path.toString() + "'";

    if (val == settingEnabled) {
      if (performExtraDebugChecks) LOG.error("nothing to change: " + initDesc);
      return false;
    }

    path.writeStringValue(appId, val ? ourTouchBarShowFnValue : null);

    if (performExtraDebugChecks) {
      // just for embedded debug: make call of Foundation.invoke(myDefaults, "synchronize") - It waits for any pending asynchronous updates to the defaults database and returns; this method is unnecessary and shouldn't be used.
      sval = path.readStringVal(appId, true);
      final boolean isFNEnabled = sval != null && sval.equals(ourTouchBarShowFnValue);
      if (val != isFNEnabled)
        LOG.error("can't write value '" + val + "' (was written just now, but read '" + sval + "'): " + initDesc);
      else
        LOG.error("value '" + val + "' was written from second attempt: " + initDesc);
    }

    return true;
  }

  public static String readStringVal(String domain, String key) {
    final Path result = new Path();
    result.myPath.add(new Path.Node("persistentDomainForName:", domain));
    return result.readStringVal(key);
  }

  public static boolean isDomainExists(String domain) {
    final Path result = new Path();
    result.myPath.add(new Path.Node("persistentDomainForName:", domain));
    return result.lastValidPos() >= 0;
  }

  public static boolean isDarkMenuBar() {
    assert SystemInfo.isMac;

    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      if (defaults == null || defaults.equals(ID.NIL))
        return false;
      final ID valObj = Foundation.invoke(defaults, "objectForKey:", Foundation.nsString("AppleInterfaceStyle"));
      if (valObj == null || valObj.equals(ID.NIL))
        return false;

      final String sval = Foundation.toStringViaUTF8(valObj);
      return sval != null && sval.equals("Dark");
    } finally {
      pool.drain();
    }
  }

  public static @NotNull Color getHighlightColor() {
    // Blue : default (key doesn't exist)
    // 0.968627 0.831373 1.000000 Purple
    // 1.000000 0.749020 0.823529 Pink
    // 1.000000 0.733333 0.721569 Red
    // 1.000000 0.874510 0.701961 Orange
    // 1.000000 0.937255 0.690196 Yellow
    // 0.752941 0.964706 0.678431 Green
    // 0.847059 0.847059 0.862745 Graphite

    final String sval = _getHighlightColor();
    if (sval == null)
      return ourDefaultHighlightColor;

    final String[] spl = sval.split(" ");
    if (spl != null && spl.length >= 3) {
      final float r,g,b;
      try {
        r = Float.parseFloat(spl[0]);
        g = Float.parseFloat(spl[1]);
        b = Float.parseFloat(spl[2]);
        return new Color(r, g, b);
      } catch (NumberFormatException nfe) {
        LOG.error(nfe);
      } catch (NullPointerException npe) {
        LOG.error(npe);
      }
    }

    LOG.error("incorrect format of registry value 'AppleHighlightColor': " + sval);
    return ourDefaultHighlightColor;
  }

  public static @Nullable Color getAccentColor() {
    if (!SystemInfo.isMac || !SystemInfo.isOsVersionAtLeast("10.14"))
      return null;

    final int nval = _getAccentColor();
    final Color result = ourAccentColId2Color.get(nval);
    return result == null ? ourDefaultAccentColor : result;
  }

  private static int _getAccentColor() {
    final String nodeName = "AppleAccentColor";
    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      if (defaults == null || defaults.equals(ID.NIL))
        return Integer.MIN_VALUE;
      final ID domObj = Foundation.invoke(defaults, "persistentDomainForName:", Foundation.nsString("Apple Global Domain"));
      if (domObj == null || domObj.equals(ID.NIL))
        return Integer.MIN_VALUE;

      final ID nskey = Foundation.nsString(nodeName);
      final ID resObj = Foundation.invoke(domObj, "objectForKey:", nskey);
      if (resObj == null || resObj.equals(ID.NIL))
        return Integer.MIN_VALUE; // key doesn't exist

      return Foundation.invoke(domObj, "integerForKey:", nskey).intValue();
    } finally {
      pool.drain();
    }
  }

  private static String _getHighlightColor() {
    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      if (defaults == null || defaults.equals(ID.NIL))
        return null;
      final ID domObj = Foundation.invoke(defaults, "persistentDomainForName:", Foundation.nsString("Apple Global Domain"));
      if (domObj == null || domObj.equals(ID.NIL))
        return null;

      final ID strObj = Foundation.invoke(domObj, "objectForKey:", Foundation.nsString("AppleHighlightColor"));
      if (strObj == null || strObj.equals(ID.NIL))
        return null;

      return Foundation.toStringViaUTF8(strObj);
    } finally {
      pool.drain();
    }
  }

  // for debug
  private List<String> _listAllKeys() {
    List<String> res = new ArrayList<String>(100);
    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      final ID allKeysDict = Foundation.invoke(defaults, "dictionaryRepresentation");
      final ID allKeysArr = Foundation.invoke(allKeysDict, "allKeys");
      final ID count = Foundation.invoke(allKeysArr, "count");
      for (int c = 0; c < count.intValue(); ++c) {
        final ID nsKeyName = Foundation.invoke(allKeysArr, "objectAtIndex:", c);
        final String keyName = Foundation.toStringViaUTF8(nsKeyName);
        //      System.out.println(keyName);
        res.add(keyName);
      }
      return res;
    } finally {
      pool.drain();
    }
  }
}
