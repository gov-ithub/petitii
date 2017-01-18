package ro.petitii.controller.api;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import ro.petitii.config.SmtpConfig;
import ro.petitii.controller.BaseController;
import ro.petitii.model.*;
import ro.petitii.model.datatables.AttachmentResponse;
import ro.petitii.model.datatables.CommentResponse;
import ro.petitii.model.datatables.EmailResponse;
import ro.petitii.model.datatables.PetitionResponse;
import ro.petitii.service.*;
import ro.petitii.service.email.SmtpService;
import ro.petitii.service.template.EmailTemplateProcessorService;
import ro.petitii.util.Pair;
import ro.petitii.util.TranslationUtil;
import ro.petitii.util.ZipUtils;

import javax.mail.MessagingException;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ro.petitii.controller.api.DatatableUtils.pageRequest;
import static ro.petitii.util.StringUtil.cleanHtml;

@Controller
@ControllerAdvice
// url base for all class methods
@RequestMapping("/api/petitions")
public class PetitionApiController extends BaseController{
    private static final Logger LOGGER = LoggerFactory.getLogger(PetitionApiController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private PetitionService petitionService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private PetitionStatusService statusService;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private EmailService emailService;


    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private EmailTemplateProcessorService emailTemplateProcessorService;

    @Autowired
    private SmtpConfig config;

    @Autowired
    private SmtpService smtpService;
    
	// will answer to compounded URL /api/petitions/user
    @RequestMapping(value = "/user", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<PetitionResponse> getUserPetitions(@Valid DataTablesInput input, String status) {
        int sequenceNo = input.getDraw();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        //TODO: catch exceptions, add  error/success message
        User user = userService.findUserByEmail(auth.getName()).get(0);

        List<PetitionStatus.Status> pStatus = parseStatus(status);

        DataTablesOutput<PetitionResponse> response = petitionService.getTableContent(user, pStatus, pageRequest(input, PetitionResponse.sortMapping));
        response.setDraw(sequenceNo);

        return response;
    }

    @RequestMapping(value = "/all", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<PetitionResponse> getAllPetitions(@Valid DataTablesInput input, String status) {
        int sequenceNo = input.getDraw();
        List<PetitionStatus.Status> pStatus = parseStatus(status);
        //TODO: catch exceptions, add  error/success message
        DataTablesOutput<PetitionResponse> response = petitionService.getTableContent(null, pStatus, pageRequest(input, PetitionResponse.sortMapping));
        response.setDraw(sequenceNo);
        return response;
    }

    @RequestMapping(value = "{id}/by/petitioner", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<PetitionResponse> getPetitionsByPetitioner(@Valid DataTablesInput input,
                                                                       @PathVariable("id") long id) {
        int sequenceNo = input.getDraw();
        //TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(id);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }

        //TODO: catch exceptions, add  error/success message
        DataTablesOutput<PetitionResponse> response = petitionService
                .getTableContent(petition, petition.getPetitioner(), pageRequest(input, PetitionResponse.sortMapping));
        response.setDraw(sequenceNo);
        return response;
    }

    @RequestMapping(value = "{id}/linked", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<PetitionResponse> getLinkedPetitions(@Valid DataTablesInput input,
                                                                 @PathVariable("id") long id) {
        int sequenceNo = input.getDraw();
        //TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(id);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        //TODO: catch exceptions, add  error/success message
        DataTablesOutput<PetitionResponse> response = petitionService
                .getTableLinkedPetitions(petition, pageRequest(input, PetitionResponse.sortMapping));
        response.setDraw(sequenceNo);
        return response;
    }

    @RequestMapping(value = "{id}/emails", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<EmailResponse> getEmails(@Valid DataTablesInput input, @PathVariable("id") long id) {
        int sequenceNo = input.getDraw();
        //TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(id);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        //TODO: catch exceptions, add  error/success message
        DataTablesOutput<EmailResponse> response = emailService.getTableContent(petition, pageRequest(input));
        response.setDraw(sequenceNo);
        return response;
    }

    @RequestMapping(value = "/{id}/attachments", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<AttachmentResponse> getAllAttachments(@PathVariable("id") Long id,
                                                                  @Valid DataTablesInput input) {
        int sequenceNo = input.getDraw();
        //TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(id);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }

        DataTablesOutput<AttachmentResponse> response = attachmentService
                .getTableContent(petition, pageRequest(input, AttachmentResponse.sortMapping));
        response.setDraw(sequenceNo);
        return response;
    }

    @RequestMapping(value = "/{id}/attachment/add", method = RequestMethod.POST)
    @ResponseBody
    public void addAttachment(@RequestParam("files") MultipartFile[] files,
                              @PathVariable("id") Long petitionId) throws IOException {

    	//TODO: catch exceptions, add  error/success message
        attachmentService.saveFromForm(files, petitionId);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(value = HttpStatus.PRECONDITION_FAILED)
    @ResponseBody
    protected ResponseEntity<String> handleMaxUploadSizeExceededException(HttpServletRequest request,
                                                                          HttpServletResponse response,
                                                                          Throwable e) throws IOException {
        LOGGER.warn("Max upload size exceeded", e);
        String message = i18n("api.controller.petition.attachment_size_exceeded");
        return ResponseEntity.unprocessableEntity().body(message);
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    protected ResponseEntity<String> handleGenericMultipartException(final HttpServletRequest request,
                                                                     final HttpServletResponse response,
                                                                     final Throwable e) throws IOException {
        Throwable rootCause = e;
        Throwable cause = e.getCause();
        while (cause != null && !cause.equals(rootCause)) {
            rootCause = cause;
            cause = cause.getCause();
        }
        LOGGER.error(rootCause.getMessage(), e);
        //TODO: verify if appropriate message
        String message = i18n("api.controller.petition.attachment_size_exceeded");
        return ResponseEntity.unprocessableEntity().body(message);
    }

    @RequestMapping("/{id}/attachments/zip")
    public void downloadAllFromPetition(@PathVariable("id") Long id, HttpServletResponse response) {
        try {
            Petition petition = petitionService.findById(id);
            if (petition == null) {
                throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
            }

            List<Pair<String, Path>> attachments = new LinkedList<>();
            for (Attachment att : petition.getAttachments()) {
                attachments.add(new Pair<>(att.getOriginalFilename(), Paths.get(att.getFilename())));
            }
            String zipFilename = "petitie-" + id + ".zip";
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
        }
    }

    @RequestMapping(value = "/{pid}/attachment/{aid}/delete", method = RequestMethod.POST)
    @ResponseBody
    public String deleteAttachment(@PathVariable("aid") Long id) {
    	//TODO: catch exceptions, add  error/success message
        attachmentService.deleteFromPetition(id);
        return "done";
    }

    private List<PetitionStatus.Status> parseStatus(String status) {
        if ("started".equalsIgnoreCase(status)) {
            List<PetitionStatus.Status> statuses = new LinkedList<>();
            statuses.add(PetitionStatus.Status.RECEIVED);
            statuses.add(PetitionStatus.Status.IN_PROGRESS);
            return statuses;
        } else {
            return null;
        }
    }

    @RequestMapping(value = "/{id}/comments", method = RequestMethod.POST)
    @ResponseBody
    public DataTablesOutput<CommentResponse> getAllComments(@PathVariable("id") Long id, @Valid DataTablesInput input) {
        int sequenceNo = input.getDraw();
        //TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(id);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        //TODO: catch exceptions, add  error/success message
        DataTablesOutput<CommentResponse> response = commentService.getTableContent(petition, pageRequest(input));
        response.setDraw(sequenceNo);
        return response;
    }

    @RequestMapping(value = "/{id}/comment/add", method = RequestMethod.POST)
    @ResponseBody
    public String addComment(@PathVariable("id") Long id, @RequestBody String commentBody) {
    	//TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(id);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        //TODO: catch exceptions, add  error/success message
        List<User> users = userService.findUserByEmail(auth.getName());
        if (users.isEmpty()) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        User user = users.get(0);
        
        //TODO: catch exceptions, add  error/success message
        commentService.createAndSave(user, petition, cleanHtml(commentBody));
        return "done";
    }

    @RequestMapping(value = "/{pid}/comment/{cid}/delete", method = RequestMethod.POST)
    @ResponseBody
    public String deleteComment(@PathVariable("cid") Long cid) {
    	//TODO: catch exceptions, add  error/success message
        commentService.delete(cid);
        return "done";
    }

    @RequestMapping(value = "/{pid}/link/{vid}", method = RequestMethod.POST)
    @ResponseBody
    public String linkPetitions(@PathVariable("pid") Long pid, @PathVariable("vid") Long vid) {
    	//TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(pid);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        //TODO: catch exceptions, add  error/success message
        Petition vassal = petitionService.findById(vid);
        if (vassal == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }

        //TODO: catch exceptions, add  error/success message
        connectionService.link(petition, vassal);
        return "done";
    }

    @RequestMapping(value = "/{pid}/unlink/{vid}", method = RequestMethod.POST)
    @ResponseBody
    public String unlinkPetitions(@PathVariable("pid") Long pid, @PathVariable("vid") Long vid) {
    	//TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(pid);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        //TODO: catch exceptions, add  error/success message
        Petition vassal = petitionService.findById(vid);
        if (vassal == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        
        //TODO: catch exceptions, add  error/success message
        connectionService.unlink(petition, vassal);
        return "done";
    }

    @RequestMapping(value = "/start-work", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> startWork(@RequestParam("petitions[]") long[] petitionIds) {
        Map<String, String> result = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        //TODO: catch exceptions, add  error/success message
        User user = userService.findUserByEmail(auth.getName()).get(0);
        List<String> errors = new LinkedList<>();

        //TODO: catch exceptions, add  error/success message
        EmailTemplate emailTemplate = emailTemplateService.findOneByCategory(EmailTemplate.Category.start_work);
        if (emailTemplate == null) {
            LOGGER.error("Email template not found for start work, using standard text messages ...");
            result.put("warnMsg", i18n("api.controller.petition.email_template_config_error"));
        }

        for (long id : petitionIds) {
        	//TODO: catch exceptions, add  error/success message
            Petition pet = petitionService.findById(id);
            if (pet == null) {
                throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
            }
            if (pet.getCurrentStatus().equals(PetitionStatus.Status.RECEIVED)) {
                statusService.create(PetitionStatus.Status.IN_PROGRESS, pet, user);
                Email email = new Email();
                email.setSender(config.getUsername());
                email.setSubject(i18n("api.controller.petition.registered_email_subject"));
                email.setRecipients(pet.getPetitioner().getEmail());

                try {
                    if (emailTemplate == null) {
                        email.setBody(createDefaultEmail(pet));
                    } else {
                        Map<String, Object> vars = new HashMap<>();
                        vars.put("pet", pet);
                        vars.put("petition", pet);
                        String body = emailTemplateProcessorService.processTemplateWithId(emailTemplate.getId(), vars);
                        if (body == null) {
                            body = createDefaultEmail(pet);
                        }
                        LOGGER.info("Sending start-work email: " + body);
                        email.setBody(body);
                    }
                    smtpService.send(email);
                } catch (MessagingException e) {
                    LOGGER.error("Could not send email with registration number " + pet.getRegNo().toString(), e);
                }
            } else {
                errors.add(pet.getRegNo().getNumber());
            }
        }

        if (errors.size() > 0) {
            result.put("success", "false");
            String errorList = errors.stream().collect(Collectors.joining(", "));
            result.put("errorMsg", i18n("api.controller.petition.status_not_updated") + ": " + errorList);
        } else {
            result.put("success", "true");
            result.put("errorMsg", i18n("api.controller.petition.status_updated"));
        }

        return result;
    }

    private String createDefaultEmail(Petition petition) {
        DateFormat df = new SimpleDateFormat("dd.MM.yyyy");
        String recDate = df.format(petition.getReceivedDate());
        String deadline = df.format(petition.getDeadline());
        
        String[] params = new String[]{petition.getRegNo().getNumber(), recDate, deadline};
        return i18n("api.controller.petition.registered", params);
    }
	
}
