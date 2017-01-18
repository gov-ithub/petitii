package ro.petitii.controller.api;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpClientErrorException;

import ro.petitii.controller.BaseController;
import ro.petitii.model.Attachment;
import ro.petitii.service.AttachmentService;

import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class AttachmentApiController extends BaseController{
    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentApiController.class);

    @Autowired
    private AttachmentService attachmentService;

    @RequestMapping("/api/attachments/download/{id}")
    public void download(@PathVariable("id") Long id, HttpServletResponse response) {
        try {
            Attachment att = attachmentService.findById(id);
            Path filepath = Paths.get(att.getFilename());
            FileInputStream is = new FileInputStream(new File(filepath.toUri()));
            response.setContentType("application/octet-stream");
            String encoded = URLEncoder.encode(att.getOriginalFilename(), "UTF-8");
            response.setHeader("Content-disposition", "attachment; filename=" + encoded);
            IOUtils.copy(is, response.getOutputStream());
            is.close();
            response.flushBuffer();
        } catch (IOException e) {
            LOGGER.error("Could not find attachment with id " + id + " on disk. ", e);
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        } catch (EntityNotFoundException e) {
            LOGGER.error("Could not find attachment with id " + id, e);
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
    }
}
