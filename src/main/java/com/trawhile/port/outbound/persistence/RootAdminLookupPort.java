package com.trawhile.port.outbound.persistence;

import java.util.UUID;

public interface RootAdminLookupPort {

    boolean anyAdminExists();

    void grantAdminOnRoot(UUID userId);
}
