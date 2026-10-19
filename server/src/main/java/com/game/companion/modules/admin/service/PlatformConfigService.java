package com.game.companion.modules.admin.service;

import com.game.companion.modules.admin.dto.PlatformConfigDTO;

public interface PlatformConfigService {

    PlatformConfigDTO getPlatformConfig();

    void savePlatformConfig(PlatformConfigDTO dto);

    String getConfig(String key, String defaultValue);
}
