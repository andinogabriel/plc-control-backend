package com.control.system.repository;

import com.control.system.domain.entity.Config;
import com.control.system.repository.filter.ConfigSearchFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ConfigRepositoryCustom {

    Page<Config> search(ConfigSearchFilter filter, Pageable pageable);
}
