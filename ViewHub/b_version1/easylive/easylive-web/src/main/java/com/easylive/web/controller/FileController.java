package com.easylive.web.controller;

import com.easylive.component.RedisComponent;
import com.easylive.entity.config.AppConfig;
import com.easylive.entity.constant.Constants;
import com.easylive.entity.dto.SysSettingDto;
import com.easylive.entity.dto.TokenUserInfoDto;
import com.easylive.entity.dto.UploadingFileDto;
import com.easylive.entity.dto.VideoPlayInfoDto;
import com.easylive.mq.VideoPlayProducer;
import com.easylive.entity.enums.DateTimePatternEnum;
import com.easylive.entity.enums.ResponseCodeEnum;
import com.easylive.entity.po.VideoInfoFile;
import com.easylive.entity.vo.ResponseVO;
import com.easylive.exception.BusinessException;
import com.easylive.service.VideoInfoFileService;
import com.easylive.utils.DateUtil;
import com.easylive.utils.FFmpegUtils;
import com.easylive.utils.StringTools;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

@RestController
@RequestMapping("/file")
@Validated
@Slf4j
@RequiredArgsConstructor
public class FileController extends ABaseController {

    private final AppConfig appConfig;

    private final RedisComponent redisComponent;

    private final FFmpegUtils ffmpegUtils;

    private final VideoInfoFileService videoInfoFileService;

    private final VideoPlayProducer videoPlayProducer;

    //获取资源
    @RequestMapping("/getResource")
    public void getResource(HttpServletResponse response, @NotNull String sourceName) {
        if (StringTools.pathIsOk(sourceName) == false) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        String suffix = StringTools.getFileSuffix(sourceName);
        //设置响应头，采用MIME类型
        if (suffix != null) {
            response.setContentType("image/" + suffix.replace(".", ""));
        } else {
            // 如果没有文件后缀，默认设置为jpeg格式
            response.setContentType("image/jpeg");
        }
        response.setHeader("Cache-Control", "max-age=2592000");
        readFile(response, sourceName);

    }

    //读文件
    protected void readFile(HttpServletResponse response, String filePath) {
        File file = new File(appConfig.getProjectFolder() + Constants.FILE_FOLDER + filePath);
        if (!file.exists()) {
            return;
        }
        try (OutputStream out = response.getOutputStream(); FileInputStream in = new FileInputStream(file)) {
            byte[] byteData = new byte[1024];
            int len = 0;
            while ((len = in.read(byteData)) != -1) {
                out.write(byteData, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            log.error("读取文件异常", e);
        }
    }

    //预上传
    @RequestMapping("/preUploadVideo")
    public ResponseVO preUploadVideo(@NotEmpty String fileName, @NotNull Integer chunks) {
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        String uploadId = redisComponent.savePreVideoFileInfo(tokenUserInfoDto.getUserId(),fileName,chunks);

        return getSuccessResponseVO(uploadId);

    }

    //正式上传
    @RequestMapping("/uploadVideo")
    public ResponseVO uploadVideo(@NotNull MultipartFile chunkFile, @NotNull Integer chunkIndex, @NotEmpty String uploadId) throws IOException {
        //从缓存中获取当前用户信息
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        //从redis中获取上传信息
        UploadingFileDto fileDto = redisComponent.getUploadingVideoFile(tokenUserInfoDto.getUserId(), uploadId);
        if(fileDto==null){
            throw new BusinessException("文件不存在，请重新上传");
        }
        //检查文件大小是否超过系统限制
        SysSettingDto sysSettingDto = redisComponent.getSysSettingDto();
        if(fileDto.getFileSize()>sysSettingDto.getVideoSize()*Constants.MB_SIZE){
            throw  new BusinessException("文件超过大小限制");
        }

        //确保分片索引不超过总分片数，并且是按顺序上传（不能跳过未上传的分片）
        if(chunkIndex>fileDto.getChunkIndex()||chunkIndex>fileDto.getChunks()-1){
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }

        //构造分片文件的存储路径
        String folder = appConfig.getProjectFolder()+Constants.FILE_FOLDER+Constants.FILE_TEMP+fileDto.getFilePath();
        File targetFile = new File(folder+"/"+chunkIndex);

        //将上传的分片保存到指定位置
        chunkFile.transferTo(targetFile);

        //更新上传进度信息
        fileDto.setChunkIndex(chunkIndex+1); // 设置下一个需要上传的分片索引
        fileDto.setFileSize(fileDto.getFileSize()+chunkFile.getSize());  //之前文件大小+一个现在分片大小    主要是用于更新传输的文件大小
        redisComponent.updateVideoFileInfo(tokenUserInfoDto.getUserId(),fileDto);

        return getSuccessResponseVO(uploadId);

    }


    //根据上传id删除上传视频
    @RequestMapping("/delUploadVideo")
    public ResponseVO delUploadVideo(@NotEmpty String uploadId) throws IOException {
        //从缓存中获取当前用户信息
        TokenUserInfoDto tokenUserInfoDto = getTokenUserInfoDto();
        //从redis中获取上传信息
        UploadingFileDto fileDto = redisComponent.getUploadingVideoFile(tokenUserInfoDto.getUserId(), uploadId);
        if(fileDto==null){
            throw new BusinessException("文件不存在，请重新上传");
        }

        //redis中删除视频信息
        redisComponent.delVideoFileInfo(tokenUserInfoDto.getUserId(),uploadId);

        //本地删除文件
        FileUtils.deleteDirectory(new File(appConfig.getProjectFolder()+Constants.FILE_FOLDER+Constants.FILE_TEMP+fileDto.getFilePath()));

        return getSuccessResponseVO(uploadId);
    }

    @RequestMapping("/uploadImage")
    //此处的代码和管理端的代码基本一样
    public ResponseVO uploadCover(@NotNull MultipartFile file, @NotNull Boolean createThumbnail) throws IOException {
        String day = DateUtil.format(new Date(), DateTimePatternEnum.YYYY_MM_DD.getPattern());
        String folder = appConfig.getProjectFolder() + Constants.FILE_FOLDER + Constants.FILE_COVER + day;
        File folderFile = new File(folder);
        if (!folderFile.exists()) {
            folderFile.mkdirs();
        }
        String fileName = file.getOriginalFilename();
        String fileSuffix = fileName.substring(fileName.lastIndexOf("."));
        String realFileName = StringTools.getRandomString(Constants.length_30) + fileSuffix;
        String filePath = folder + "/" + realFileName;
        file.transferTo(new File(filePath));
        if (createThumbnail) {
            //生成缩略图
            ffmpegUtils.createImageThumbnail(filePath);
        }
        return getSuccessResponseVO(Constants.FILE_COVER + day + "/" + realFileName);
    }

    @RequestMapping("/videoResource/{fileId}")
    public void VideoResource(HttpServletResponse response, @PathVariable @NotEmpty String fileId){
        VideoInfoFile videoInfoFile = videoInfoFileService.getVideoInfoFileByFileId(fileId);
        String filePath = videoInfoFile.getFilePath();
        readFile(response, filePath+"/"+Constants.M3U8_NAME);

        //更新视频的阅读信息等等
        VideoPlayInfoDto videoPlayInfoDto = new VideoPlayInfoDto();
        videoPlayInfoDto.setVideoId(videoInfoFile.getVideoId());
        videoPlayInfoDto.setFileIndex(videoInfoFile.getFileIndex());
        //getTokenUserInfoDto();是为了取出当前登录用户的信息。。但是此处的目的是为了获取视频的用户信息..此处是播放器发起的请求
        TokenUserInfoDto tokenUserInfoDto = getTokenInfoFromCookie();

        if (tokenUserInfoDto != null) {
            videoPlayInfoDto.setUserId(tokenUserInfoDto.getUserId());
        }
        
        // 发送视频播放统计任务到RabbitMQ（异步处理）
        try {
            videoPlayProducer.sendVideoPlayTask(videoPlayInfoDto);
            log.debug("视频播放统计任务已发送到MQ, videoId={}, userId={}", 
                videoPlayInfoDto.getVideoId(), videoPlayInfoDto.getUserId());
        } catch (Exception e) {
            log.error("发送视频播放统计任务到MQ失败, videoId={}, userId={}", 
                videoPlayInfoDto.getVideoId(), videoPlayInfoDto.getUserId(), e);
            // 发送失败不影响主流程，只记录日志
        }
    }

    @RequestMapping("/videoResource/{fileId}/{ts}")
    public void VideoResourceTs(HttpServletResponse response, @PathVariable @NotEmpty String fileId, @PathVariable @NotNull String ts) {
        VideoInfoFile videoInfoFile = videoInfoFileService.getVideoInfoFileByFileId(fileId);
        String filePath = videoInfoFile.getFilePath();
        readFile(response, filePath + "/" + ts);
    }


}