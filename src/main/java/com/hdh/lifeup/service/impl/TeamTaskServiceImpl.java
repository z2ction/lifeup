package com.hdh.lifeup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.base.Preconditions;
import com.hdh.lifeup.auth.UserContext;
import com.hdh.lifeup.base.BaseDTO;
import com.hdh.lifeup.constant.TeamRole;
import com.hdh.lifeup.domain.TeamRecordDO;
import com.hdh.lifeup.domain.TeamTaskDO;
import com.hdh.lifeup.dto.PageDTO;
import com.hdh.lifeup.dto.TeamMemberDTO;
import com.hdh.lifeup.dto.TeamTaskDTO;
import com.hdh.lifeup.dto.UserInfoDTO;
import com.hdh.lifeup.enums.CodeMsgEnum;
import com.hdh.lifeup.exception.GlobalException;
import com.hdh.lifeup.mapper.TeamRecordMapper;
import com.hdh.lifeup.mapper.TeamTaskMapper;
import com.hdh.lifeup.service.TeamMemberService;
import com.hdh.lifeup.service.TeamTaskService;
import com.hdh.lifeup.service.UserInfoService;
import com.hdh.lifeup.vo.NextSignVO;
import com.hdh.lifeup.vo.TeamDetailVO;
import com.hdh.lifeup.vo.TeamTaskVO;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * TeamTaskServiceImpl class<br/>
 *
 * @author hdonghong
 * @since 2018/09/03
 */
@Slf4j
@Service
public class TeamTaskServiceImpl implements TeamTaskService {

    private TeamTaskMapper teamTaskMapper;

    private TeamRecordMapper teamRecordMapper;

    private TeamMemberService teamMemberService;

    private UserInfoService userInfoService;

    @Autowired
    public TeamTaskServiceImpl(TeamTaskMapper teamTaskMapper,
                               TeamRecordMapper teamRecordMapper,
                               TeamMemberService teamMemberService,
                               UserInfoService userInfoService) {
        this.teamTaskMapper = teamTaskMapper;
        this.teamRecordMapper = teamRecordMapper;
        this.teamMemberService = teamMemberService;
        this.userInfoService = userInfoService;
    }

    @Override
    public TeamTaskDTO getOne(Long teamId) {
        Preconditions.checkNotNull(teamId, "[TeamTaskServiceImpl.getOne] teamId is null");
        TeamTaskDO teamTaskDO = teamTaskMapper.selectById(teamId);
        if (teamTaskDO == null) {
            log.error("【TeamTaskServiceImpl.getOne】不存在的团队任务，teamId = [{}]", teamId);
            throw new GlobalException(CodeMsgEnum.TEAM_NOT_EXIST);
        }
        return BaseDTO.from(teamTaskDO, TeamTaskDTO.class);
    }

    @Override
    public <T> List<TeamTaskDTO> listByConditions(T queryCondition) {
        return null;
    }

    @Override
    public <T> PageDTO<TeamTaskDTO> pageByConditions(T queryCondition, int currPage, int pageSize) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamTaskDTO insert(@NonNull TeamTaskDTO teamTaskDTO) {
        TeamTaskDO teamTaskDO = teamTaskDTO.toDO(TeamTaskDO.class);
        Integer result = teamTaskMapper.insert(teamTaskDO);
        if (!Objects.equals(1, result)) {
            log.error("【TeamTaskServiceImpl.insert】插入新teamTask记录失败，teamTaskDTO = [{}]", teamTaskDTO);
            throw new GlobalException(CodeMsgEnum.DATABASE_EXCEPTION);
        }
        teamTaskDTO.setTeamId(teamTaskDO.getTeamId());
        return teamTaskDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NextSignVO addTeam(@NonNull TeamTaskVO teamTaskVO) {
        TeamTaskDTO teamTaskDTO = new TeamTaskDTO();
        BeanUtils.copyProperties(teamTaskVO, teamTaskDTO, "firstStartTime", "firstEndTime");

        // 存主团队表
        LocalDateTime firstStartTime = teamTaskVO.getFirstStartTime();
        LocalDateTime firstEndTime = teamTaskVO.getFirstEndTime();
        teamTaskDTO.setStartTime(firstStartTime.toLocalTime())
                   .setStartTime(firstEndTime.toLocalTime())
                   .setUserId(UserContext.get().getUserId())
                   .setTeamHead(UserContext.get().getUserHead());
        this.insert(teamTaskDTO);

        // 存团队记录情况表
        TeamRecordDO teamRecordDO = new TeamRecordDO().setNextStartTime(firstStartTime)
                                                      .setNextEndTime(firstEndTime)
                                                      .setTeamId(teamTaskDTO.getTeamId());
        teamRecordMapper.insert(teamRecordDO);

        // 创建者作为新成员写入成员表
        TeamMemberDTO teamMemberDTO = new TeamMemberDTO()
                                            .setTeamId(teamTaskDTO.getTeamId())
                                            .setUserId(UserContext.get().getUserId())
                                            .setTeamRole(TeamRole.MEMBER);
        teamMemberService.insert(teamMemberDTO);

        return new NextSignVO().setTeamId(teamTaskDTO.getTeamId())
                               .setNextStartTime(firstStartTime)
                               .setNextEndTime(firstEndTime);

    }

    @Override
    public PageDTO<TeamTaskDTO> page(PageDTO pageDTO) {
        IPage<TeamTaskDO> taskDOPage = teamTaskMapper.selectPage(
                new Page<>(pageDTO.getCurrentPage(), pageDTO.getSize()),
                new QueryWrapper<TeamTaskDO>().eq("user_id", UserContext.get().getUserId())
        );
        return PageDTO.create(taskDOPage, TeamTaskDTO.class);
    }

    @Override
    public TeamDetailVO getDetail(@NonNull Long teamId) {
        TeamTaskDTO teamTaskDTO = this.getOne(teamId);
        int memberAmount = teamMemberService.countByTeamId(teamId);
        UserInfoDTO owner = userInfoService.getOne(teamTaskDTO.getUserId());
        NextSignVO nextSign = getNextSign(teamId);

        TeamDetailVO teamDetailVO = new TeamDetailVO();
        BeanUtils.copyProperties(teamTaskDTO, teamDetailVO);
        teamDetailVO.setMemberAmount(memberAmount)
                    .setOwner(owner)
                    .setNextStartTime(nextSign.getNextStartTime())
                    .setNextEndTime(nextSign.getNextEndTime());
        return teamDetailVO;
    }

    @Override
    public NextSignVO getNextSign(Long teamId) {
        TeamRecordDO teamRecordDO = teamRecordMapper.selectOne(
                new QueryWrapper<TeamRecordDO>().eq("team_id", teamId)
                        .orderByDesc("create_time")
        );
        if (teamRecordDO == null) {
            log.error("【获取下一次签到信息】不存在的团队，teamId = [{}]", teamId);
            throw new GlobalException(CodeMsgEnum.TEAM_NOT_EXIST);
        }
        NextSignVO nextSignVO = new NextSignVO();
        BeanUtils.copyProperties(teamRecordDO, nextSignVO);
        return nextSignVO;
    }

    @Override
    public TeamTaskDTO update(TeamTaskDTO dto) {
        return null;
    }

    @Override
    public TeamTaskDTO deleteLogically(Long aLong) {
        return null;
    }

    @Override
    public TeamTaskDTO delete(Long aLong) {
        return null;
    }
}
