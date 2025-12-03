package fr.maxlego08.zsupport.role;

import java.util.ArrayList;
import java.util.List;

public class RoleUser {

    private final long id;
    private final List<Long> roles = new ArrayList<Long>();

    public RoleUser(long id) {
        super();
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public List<Long> getRoles() {
        return roles;
    }

    public boolean is(long id) {
        return roles.contains(id);
    }

    public void add(long id) {
        this.roles.add(id);
    }
	
    public void remove(long id) {
        this.roles.remove(id);
    }
}
