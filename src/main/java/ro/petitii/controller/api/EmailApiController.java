package ro.petitii.controller.api;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import ro.petitii.controller.BaseController;
import ro.petitii.model.Attachment;
import ro.petitii.model.Email;
import ro.petitii.model.datatables.EmailResponse;
import ro.petitii.service.EmailService;
import ro.petitii.service.email.ImapService;
import ro.petitii.util.Pair;
import ro.petitii.util.ZipUtils;

import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static ro.petitii.controller.api.DatatableUtils.pageRequest;

@RestController
public class EmailApiController extends BaseController{
    @Autowired
    private EmailService emailService;

    @Autowired
    private ImapService imapService;


    private static final Logger LOGGER = LoggerFactory.getLogger(EmailApiController.class);

    @RequestMapping(value = "/api/emails", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<EmailResponse> getInbox(@Valid DataTablesInput input) {
        int sequenceNo = input.getDraw();
        //TODO: catch exceptions, add  error/success message
        DataTablesOutput<EmailResponse> response = emailService.getTableContent(Email.EmailType.Inbox, pageRequest(input));
        response.setDraw(sequenceNo);
        return response;
    }

    @RequestMapping(value = "/api/spam", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<EmailResponse> getSpam(@Valid DataTablesInput input) {
        int sequenceNo = input.getDraw();

        //TODO: catch exceptions, add  error/success message
        DataTablesOutput<EmailResponse> response = emailService.getTableContent(Email.EmailType.Spam, pageRequest(input));
        response.setDraw(sequenceNo);
        return response;
    }

    @RequestMapping(value = "/api/markAs/{type}/{id}", method = RequestMethod.POST)
    @ResponseBody
    public String markSpam(@PathVariable("type") String type, @PathVariable("id") Long id) {
        Email.EmailType emailType = null;
        if ("email".equalsIgnoreCase(type)) {
            emailType = Email.EmailType.Inbox;
        } else if ("spam".equalsIgnoreCase(type)) {
            emailType = Email.EmailType.Spam;
        }

        if (emailType == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }

        Email email = emailService.searchById(id);
        if (email == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        email.setType(emailType);
        //TODO: catch exceptions, add  error/success message
        emailService.save(email);
        return "OK";
    }

    @RequestMapping("/api/refresh")
    @ResponseBody
    public Map<String, String> inboxRefresh() {
        Map<String, String> result = new HashMap<>();
        try {
            imapService.getMail();
        } catch (Exception e) {
        	LOGGER.error("Cannot read from inbox:", e);
            result.put("error", e.getClass().getName());
            result.put("errorMsg", e.getMessage());
        }
        if (!result.containsKey("error")) {
            result.put("success", "true");
        } else {
            result.put("success", "false");
        }
        return result;
    }

    @RequestMapping("/api/email/{id}/attachments/zip")
    public void downloadAllFromEmail(@PathVariable("id") Long id, HttpServletResponse response) {
        try {
        	Email email = emailService.searchById(id);
            if (email == null) {
                throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
            }

            List<Pair<String, Path>> attachments = new LinkedList<>();
            for (Attachment att : email.getAttachments()) {
                attachments.add(new Pair<>(att.getOriginalFilename(), Paths.get(att.getFilename())));
            }
            String zipFilename = "email-" + id + ".zip";
            InputStream is = ZipUtils.create(attachments);
            response.setContentType("application/octet-stream");
            response.setHeader("Content-disposition", "attachment; filename=" + zipFilename);
            IOUtils.copy(is, response.getOutputStream());
            is.close();
            response.flushBuffer();
        } catch (IOException e) {
            LOGGER.error("Could not find attachment with id " + id + " on disk", e);
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        } catch (EntityNotFoundException e) {
            LOGGER.error("Could not find attachment with id " + id, e);
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            LOGGER.error("Could not download attachment with id " + id, e);
            throw new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        
    }
}
