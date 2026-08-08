package fr.maxlego08.zsupport.utils.storage;

import fr.maxlego08.zsupport.ZSupport;

import java.io.File;
import java.lang.reflect.Type;

public class Persist {

    private final ZSupport main;

    public Persist(ZSupport main) {
        super();
        this.main = main;
    }

    // ------------------------------------------------------------ //
    // GET NAME - What should we call this type of object?
    // ------------------------------------------------------------ //

    public static String getName(Class<?> clazz) {
        return clazz.getSimpleName().toLowerCase();
    }

    public static String getName(Object o) {
        return getName(o.getClass());
    }

    public static String getName(Type type) {
        return getName(type.getClass());
    }

    // ------------------------------------------------------------ //
    // GET FILE - In which file would we like to store this object?
    // ------------------------------------------------------------ //

    public File getFile(String name) {
        return new File(name + ".json");
    }

    public File getFile(Class<?> clazz) {
        return getFile(getName(clazz));
    }

    public File getFile(Object obj) {
        return getFile(getName(obj));
    }

    public File getFile(Type type) {
        return getFile(getName(type));
    }


    // NICE WRAPPERS

    public <T> T loadOrSaveDefault(T def, Class<T> clazz) {
        return loadOrSaveDefault(def, clazz, getFile(clazz));
    }

    public <T> T loadOrSaveDefault(T def, Class<T> clazz, String name) {
        return loadOrSaveDefault(def, clazz, getFile(name));
    }

    public <T> T loadOrSaveDefault(T def, Class<T> clazz, File file) {
        if (!file.exists()) {
            System.out.println("[zSupport] Creating default: " + file);
            this.save(def, file);
            return def;
        }

        System.out.println("Using config file " + file.getAbsolutePath());

        T loaded = this.load(clazz, file);

        if (loaded == null) {
            System.out.println("[zSupport] Using default as I failed to load: " + file);
            /*
             * Create new config backup
             * */

            File backup = new File(file.getPath() + "_bad");
            if (backup.exists()) backup.delete();
            System.out.println("[zSupport] Backing up copy of bad file to: " + backup);
            file.renameTo(backup);

            return def;
        } else {

            System.out.println("[zSupport] " + file.getAbsolutePath() + " loaded successfully !");

        }

        return loaded;
    }

    // SAVE

    public boolean save(Object instance) {
        return save(instance, getFile(instance));
    }

    public boolean save(Object instance, String name) {
        return save(instance, getFile(name));
    }

    public boolean save(Object instance, File file) {
        return DiscUtil.writeCatch(file, main.getGson().toJson(instance));
    }

    // LOAD BY CLASS

    public <T> T load(Class<T> clazz) {
        return load(clazz, getFile(clazz));
    }

    public <T> T load(Class<T> clazz, String name) {
        return load(clazz, getFile(name));
    }

    public <T> T load(Class<T> clazz, File file) {
        System.out.println("File: " + file.getAbsolutePath());
        String content = DiscUtil.readCatch(file);
        if (content == null) {
            return null;
        }

        try {
            T instance = main.getGson().fromJson(content, clazz);
            return instance;
        } catch (
                Exception ex) {    // output the error message rather than full stack trace; error parsing the file, most likely
            ex.printStackTrace();
        }

        return null;
    }


    // LOAD BY TYPE
    @SuppressWarnings("unchecked")
    public <T> T load(Type typeOfT, String name) {
        return load(typeOfT, getFile(name));
    }

    @SuppressWarnings("unchecked")
    public <T> T load(Type typeOfT, File file) {
        String content = DiscUtil.readCatch(file);
        if (content == null) {
            return null;
        }

        try {
            return main.getGson().fromJson(content, typeOfT);
        } catch (
                Exception ex) {    // output the error message rather than full stack trace; error parsing the file, most likely
            ex.printStackTrace();
        }

        return null;
    }

}