package net.ctrdn.talk.portal.menu;

import java.util.ArrayList;
import java.util.List;

public class MenuItem {

    private final String title;
    private final String icon;
    private final String url;
    private final boolean targetBlank;
    private final boolean adminOnly;
    private final List<MenuItem> subItemList = new ArrayList<>();

    public MenuItem(String title, String url, String icon, boolean adminOnly, boolean targetBlank) {
        this.title = title;
        this.url = url;
        this.icon = icon;
        this.targetBlank = targetBlank;
        this.adminOnly = adminOnly;
    }

    public MenuItem(String title, String url, String icon, boolean adminOnly) {
        this(title, url, icon, adminOnly, false);
    }

    public MenuItem(String title, String url, String icon) {
        this(title, url, icon, false, false);
    }

    public MenuItem(String title, String url) {
        this(title, url, null);
    }

    public void addSubItem(MenuItem mi) {
        this.subItemList.add(mi);
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }

    public String getUrl() {
        return url;
    }

    public String toHtmlString(boolean isAdmin, String currentUrl) {
        if (this.adminOnly && !isAdmin) {
            return "";
        }
        String activeHtml = (currentUrl.equals(this.getUrl())) ? "active" : "";
        String html = "";
        if (this.subItemList.size() > 0) {
            html += "<li class=\"" + activeHtml + " parent\">";
            html += "<a href=\"#\">";
            if (this.getIcon() != null) {
                html += "<i class=\"fa " + this.getIcon() + "\"></i> ";
            }
            html += "<span>" + this.getTitle() + "</span></a>";
            html += "<ul class=\"sub-menu\">";
            for (MenuItem mi : this.subItemList) {
                html += mi.toSubmenuHtmlString(isAdmin, currentUrl);
            }
            html += "</ul></a>";
        } else {
            html += "<li class=\"" + activeHtml + "\">";
            html += "<a href=\"" + this.getUrl() + "\"" + ((this.targetBlank) ? "target=\"_blank\"" : "") + ">";
            if (this.getIcon() != null) {
                html += "<i class=\"fa " + this.getIcon() + "\"></i> ";
            }
            html += "<span>" + this.getTitle() + "</span></a>";
        }
        html += "</li>";
        return html;
    }

    private String toSubmenuHtmlString(boolean isAdmin, String currentUrl) {
        if (this.adminOnly && !isAdmin) {
            return "";
        }
        String activeHtml = (currentUrl.equals(this.getUrl())) ? " class=\"active\"" : "";
        String html = "<li" + activeHtml + ">";
        html += "<a href=\"" + this.getUrl() + "\"" + ((this.targetBlank) ? "target=\"_blank\"" : "") + ">" + this.getTitle() + "</a>";
        html += "</li>";
        return html;
    }
}
