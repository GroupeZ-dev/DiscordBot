package fr.maxlego08.zsupport.plugins;

import fr.maxlego08.zsupport.Config;

public class Resource {

    private final int id;
    private final String name;
    private final int price;
    private final String image;
    private final String description;
    private final String created_at;
    private final String updated_at;
    private final String tag;
    private final int user_id;
    private final int version_id;
    private final int category_id;
    private final int download;
    private final int purchases;
    private final int is_display;
    private final int is_pending;
    private final String file;
    private final String unique_id;
    private final String logo;
    private final Version version;

    public Resource(int id, String name, int price, String image, String description, String created_at, String updated_at, String tag, int user_id, int version_id, int category_id, int download, int purchases, int is_display, int is_pending, String file, String unique_id, String logo, Version version) {
        super();
        this.id = id;
        this.name = name;
        this.price = price;
        this.image = image;
        this.description = description;
        this.created_at = created_at;
        this.updated_at = updated_at;
        this.tag = tag;
        this.user_id = user_id;
        this.version_id = version_id;
        this.category_id = category_id;
        this.download = download;
        this.purchases = purchases;
        this.is_display = is_display;
        this.is_pending = is_pending;
        this.file = file;
        this.unique_id = unique_id;
        this.logo = logo;
        this.version = version;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public String getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }

    public String getCreated_at() {
        return created_at;
    }

    public String getUpdated_at() {
        return updated_at;
    }

    public String getTag() {
        return tag;
    }

    public int getUser_id() {
        return user_id;
    }

    public int getVersion_id() {
        return version_id;
    }

    public int getCategory_id() {
        return category_id;
    }

    public int getDownload() {
        return download;
    }

    public int getPurchases() {
        return purchases;
    }

    public int getIs_display() {
        return is_display;
    }

    public int getIs_pending() {
        return is_pending;
    }

    public String getFile() {
        return file;
    }

    public String getUnique_id() {
        return unique_id;
    }

    public String getLogo() {
        return logo;
    }

    public Version getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Resource [id=" + id + ", name=" + name + ", price=" + price + ", image=" + image + ", created_at=" + created_at + ", updated_at=" + updated_at + ", tag=" + tag + ", user_id=" + user_id + ", version_id=" + version_id + ", category_id=" + category_id + ", download=" + download + ", purchases=" + purchases + ", is_display=" + is_display + ", is_pending=" + is_pending + ", file=" + file + ", unique_id=" + unique_id + ", logo=" + logo + ", version=" + version + "]";
    }

    public String getResourceUrl() {
        return String.format(Config.RESOURCE_URL, this.id);
    }

}
