package net.ctrdn.talk.portal.menu;

import java.util.ArrayList;
import java.util.List;

public class MenuItem {

    private final String title;
    private final String icon;
    private final String url;
    private final List<MenuItem> subItemList = new ArrayList<>();

    public MenuItem(String title, String url, String icon) {
        this.title = title;
        this.url = url;
        this.icon = icon;
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

    public String toHtmlString(String currentUrl) {
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
                html += mi.toSubmenuHtmlString(currentUrl);
            }
            html += "</ul></a>";
        } else {
            html += "<li class=\"" + activeHtml + "\">";
            html += "<a href=\"" + this.getUrl() + "\">";
            if (this.getIcon() != null) {
                html += "<i class=\"fa " + this.getIcon() + "\"></i> ";
            }
            html += "<span>" + this.getTitle() + "</span></a>";
        }
        html += "</li>";
        return html;
    }

    private String toSubmenuHtmlString(String currentUrl) {
        String activeHtml = (currentUrl.equals(this.getUrl())) ? " class=\"active\"" : "";
        String html = "<li" + activeHtml + ">";
        html += "<a href=\"" + this.getUrl() + "\">" + this.getTitle() + "</a>";
        html += "</li>";
        return html;
    }
}
