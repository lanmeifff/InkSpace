package com.inkspace.controller;

import com.inkspace.common.api.Result;
import com.inkspace.common.security.CurrentUser;
import com.inkspace.service.StatsService;
import com.inkspace.vo.DayCountVO;
import com.inkspace.vo.StatsOverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仪表盘统计接口。
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.ok(statsService.overview(CurrentUser.id()));
    }

    @GetMapping("/heatmap")
    public Result<List<DayCountVO>> heatmap(@RequestParam(defaultValue = "26") int weeks) {
        return Result.ok(statsService.heatmap(CurrentUser.id(), Math.min(Math.max(weeks, 4), 52)));
    }
}
