package com.oa.config;

import com.oa.entity.JobLevel;
import com.oa.entity.JobTitle;
import com.oa.repository.JobLevelRepository;
import com.oa.repository.JobTitleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 职级/职称主数据预置（SY-02，幂等：按 code）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(250)
public class JobMasterDataSeeder implements ApplicationRunner {

    private final JobLevelRepository jobLevelRepository;
    private final JobTitleRepository jobTitleRepository;

    private static final List<String[]> LEVELS = List.of(
            new String[]{"L1", "一级"},
            new String[]{"L2", "二级"},
            new String[]{"L3", "三级"},
            new String[]{"L4", "四级"}
    );

    private static final List<String[]> TITLES = List.of(
            new String[]{"ENGINEER", "工程师"},
            new String[]{"SENIOR_ENGINEER", "高级工程师"},
            new String[]{"CHIEF_ENGINEER", "主任工程师"},
            new String[]{"TECH_DIRECTOR", "技术总监"},
            new String[]{"PRODUCT_MANAGER", "产品经理"},
            new String[]{"HR", "人力资源"},
            new String[]{"FINANCE", "财务"}
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (String[] lv : LEVELS) {
            if (jobLevelRepository.findByCode(lv[0]).isEmpty()) {
                JobLevel l = new JobLevel();
                l.setCode(lv[0]);
                l.setName(lv[1]);
                jobLevelRepository.save(l);
                created++;
            }
        }
        int createdTitles = 0;
        for (String[] t : TITLES) {
            if (jobTitleRepository.findByCode(t[0]).isEmpty()) {
                JobTitle jt = new JobTitle();
                jt.setCode(t[0]);
                jt.setName(t[1]);
                jobTitleRepository.save(jt);
                createdTitles++;
            }
        }
        if (created > 0 || createdTitles > 0) {
            log.info("职级/职称预置完成：职级 {} 条、职称 {} 条",
                    jobLevelRepository.count(), jobTitleRepository.count());
        }
    }
}
