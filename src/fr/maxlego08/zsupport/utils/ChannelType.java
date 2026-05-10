package fr.maxlego08.zsupport.utils;

public enum ChannelType {

    FREE("Community support", "You are in a community support channel for a free plugin.\nPlease check if someone hasn't already asked your question in the channel.\nPlease don't **mention the staff**. Please wait for an answer."),
    PREMIUM("Community support", "You are in a community support channel for a premium plugin. To get help faster, please create a ticket https://discord.com/channels/511516467615760405/712305238748692572"),

    GENERAL("How to get help ?", "Do you need help ? Please create a %s and do not request help here. You can ask your questions before a purchase here."),

    ;

    private final String title;
    private final String description;

    ChannelType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

}