package com.trawhile;

import com.trawhile.lifecycle.ActivityPurgeJob;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    @Test
    @Tag("TE-F050.F01-02")
    void activityPurgeJob_usesConfiguredPurgeCronInCompanyTimezone() throws NoSuchMethodException {
        Method trigger = ActivityPurgeJob.class.getMethod("trigger");

        Scheduled scheduled = trigger.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${trawhile.purge-cron:0 59 23 * * *}");
        assertThat(scheduled.zone()).isEqualTo("${trawhile.timezone:UTC}");
    }
}
