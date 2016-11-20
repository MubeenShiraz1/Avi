package com.x1unix.avi.model;

import com.google.gson.annotations.SerializedName;

public class KPPeople {
    @SerializedName("id")
    private static String id;

    @SerializedName("type")
    private static String type;

    @SerializedName("nameRU")
    private static String nameRU;

    @SerializedName("nameEN")
    private static String nameEN;

    @SerializedName("professionKey")
    private static String professionKey;

    public KPPeople(String iid, String itype, String inameRU, String inameEN, String iprofessionKey) {
        id = iid;
        type = itype;
        nameRU = inameRU;
        nameEN = inameEN;
        professionKey = iprofessionKey;
    }

    public String getName(String currentLocale) {
        Boolean isSlavic = ( currentLocale.equals("ru") || currentLocale.equals("uk") );
        Boolean isSlavicAvailable = (nameRU != null) || (nameRU.length() > 0);
        Boolean isLatinAvailable = (nameEN != null) || (nameEN.length() > 0);

        String value;

        if (isSlavic) {
            if (isSlavicAvailable) {
                value = nameRU;
            } else {
                value = nameEN;
            }
        } else {
            if (isLatinAvailable) {
                value = nameEN;
            } else {
                value = nameRU;
            }
        }

        return value;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getRole() {
        return (professionKey == null) ? "unknown" : professionKey;
    }

    public boolean isActor() {
        return getRole().equals("actor");
    }

    public boolean isDirector() {
        return getRole().equals("director");
    }

    public boolean isProducer() {
        return getRole().equals("producer");
    }
}
