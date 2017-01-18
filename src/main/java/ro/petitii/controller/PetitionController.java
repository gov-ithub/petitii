package ro.petitii.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ro.petitii.config.DeadlineConfig;
import ro.petitii.config.DefaultsConfig;
import ro.petitii.config.SmtpConfig;
import ro.petitii.model.*;
import ro.petitii.service.*;
import ro.petitii.service.email.SmtpService;
import ro.petitii.util.DateUtil;
import ro.petitii.util.ValidationStatus;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Controller
public class PetitionController extends ViewController {
    @Autowired
    private UserService userService;

    @Autowired
    private PetitionService petitionService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private DefaultsConfig defaultsConfig;

    @Autowired
    private PetitionCustomParamService petitionCustomParamService;

    @Autowired
    private ContactService contactService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private PetitionStatusService statusService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private SmtpService smtpService;

    @Autowired
    private SmtpConfig smtpConfig;

    @Autowired
    private DeadlineConfig deadlineConfig;

    private static final Logger LOGGER = LoggerFactory.getLogger(PetitionController.class);
	
    @RequestMapping(path = "/petition", method = RequestMethod.GET)
    public ModelAndView addPetition() {
        Petitioner petitioner = new Petitioner();
        petitioner.setCountry(defaultsConfig.getCountry());

        Petition petition = new Petition();
        petition.setReceivedDate(new Date());
        petition.setDeadline(DateUtil.deadline(new Date(), deadlineConfig.getDays()));
        petition.setPetitioner(petitioner);

        petitionCustomParamService.initDefaults(petition);

        ModelAndView modelAndView = new ModelAndView("petitions_crud");
        modelAndView.addObject("petition", petition);

        addCustomParams(modelAndView);

        return modelAndView;
    }

    @RequestMapping(path = "/petition/{id}", method = RequestMethod.GET)
    public ModelAndView editPetition(@PathVariable("id") Long id) {
        ModelAndView modelAndView = new ModelAndView("petitions_crud");

      //TODO: catch exceptions, add  error message
        Petition petition = petitionService.findById(id);
        modelAndView.addObject("petition", petition);
        modelAndView.addObject("commentsApiUrl", "/api/petitions/" + petition.getId() + "/comments");
        modelAndView.addObject("attachmentApiUrl", "/api/petitions/" + petition.getId() + "/attachments");
        modelAndView.addObject("linkedPetitionsApiUrl", "/api/petitions/" + petition.getId() + "/linked");
        modelAndView.addObject("linkedPetitionerApiUrl", "/api/petitions/" + petition.getId() + "/by/petitioner");
        modelAndView.addObject("emailsApiUrl", "/api/petitions/" + petition.getId() + "/emails");

        addCustomParams(modelAndView);

        return modelAndView;
    }

    @RequestMapping(path = "/petition/fromEmail/{id}", method = RequestMethod.GET)
    public ModelAndView createPetitionFromEmail(@PathVariable("id") Long id, HttpServletRequest request, final RedirectAttributes attr) {
        ModelAndView modelAndView = new ModelAndView();
        //TODO: catch exceptions, add  error/success message
        Email email = emailService.searchById(id);
        if (email.getPetition()!=null) {
        	attr.addFlashAttribute("toast", i18nToast("controller.petition.petition_exists_for_email", ToastType.danger));
            modelAndView.setViewName("redirect:" + request.getHeader("referer"));
            return modelAndView;
        }

        //TODO: catch exceptions, add  error message
        Petition petition = petitionService.createFromEmail(email);
        petitionCustomParamService.initDefaults(petition);

        modelAndView.setViewName("petitions_crud");
        modelAndView.addObject("petition", petition);

        addCustomParams(modelAndView);

        return modelAndView;
    }

    @RequestMapping(path = "/petition", method = RequestMethod.POST)
    public ModelAndView savePetition(@Valid Petition petition, BindingResult bindingResult,
                                     final RedirectAttributes attr) {
        ModelAndView modelAndView = new ModelAndView();

        petitionCustomParamService.validate(petition, bindingResult);

        if (bindingResult.hasErrors()) {
            modelAndView.setViewName("petitions_crud");
            addCustomParams(modelAndView);
            modelAndView.addObject("petition", petition);
            modelAndView.addObject("toast", i18nToast("controller.petition.petition_not_saved", ToastType.danger));
            String serializedErrors = Arrays.toString(bindingResult.getAllErrors().toArray());
            LOGGER.debug(i18n("controller.petition.petition_not_saved") + "\n" + serializedErrors);
        } else {
            petition = petitionService.save(petition);
            modelAndView.setViewName("redirect:/petition/" + petition.getId());
            attr.addFlashAttribute("toast", i18nToast("controller.petition.petition_saved", ToastType.success));
        }

        return modelAndView;
    }

    @RequestMapping("/petitions")
    public ModelAndView listUserPetitions() {
        ModelAndView modelAndView = new ModelAndView("petitions_list");
        modelAndView.addObject("apiUrl", "/api/petitions/user");
        return modelAndView;
    }

    @RequestMapping("/petitions/all")
    public ModelAndView listAllPetitions() {
        ModelAndView modelAndView = new ModelAndView("petitions_list");
        modelAndView.addObject("apiUrl", "/api/petitions/all");
        return modelAndView;
    }

    @RequestMapping(value = "/petition/redirect/{id}", method = RequestMethod.GET)
    public ModelAndView redirectPetition(@PathVariable("id") long id) {
        ModelAndView modelAndView = new ModelAndView("petitions_redirect");
        //TODO: catch exceptions, add  error message
        Petition petition = petitionService.findById(id);
        modelAndView.addObject("petition", petition);
        List<Contact> contactList = (List<Contact>) (contactService.getAllContacts());
        modelAndView.addObject("contacts", contactList);
        modelAndView.addObject("templateList", emailTemplateService.findByCategory(EmailTemplate.Category.forward));
        return modelAndView;
    }

    @RequestMapping(value = "/petition/redirect/{id}", method = RequestMethod.POST)
    @ResponseBody
    public ModelAndView redirectPetition(@PathVariable("id") long id,
                                         @RequestParam("subject") String subject,
                                         @RequestParam("recipients") long[] recipients,
                                         @RequestParam(value = "attachments[]", required = false) Long[] attachments,
                                         @RequestParam("description") String description,
                                         final RedirectAttributes attr) {

        ModelAndView modelAndView = new ModelAndView();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        //TODO: catch exceptions, add  error message
        User user = userService.findUserByEmail(auth.getName()).get(0);
        Petition petition = petitionService.findById(id);
        modelAndView.setViewName("redirect:/petition/" + petition.getId());

        statusService.create(PetitionStatus.Status.REDIRECTED, petition, user);

        try {
            smtpService.send(createEmail(subject, description, convertRecipients(recipients), attachments, petition));
        	attr.addFlashAttribute("toast", i18nToast("controller.petition.petition_redirected", ToastType.success));
        } catch (MessagingException e) {
        	attr.addFlashAttribute("toast", i18nToast("controller.petition.petition_not_redirected", ToastType.danger, e.getMessage()));
        	LOGGER.error(i18n("controller.petition.petition_not_redirected"), e);
        }

        return modelAndView;
    }

    @RequestMapping(value = "/petition/{pid}/resolve/{action}", method = RequestMethod.GET)
    public ModelAndView resolve(@PathVariable("pid") Long pid, @PathVariable("action") String action,
                                final RedirectAttributes attr) {
        Petition petition = petitionService.findById(pid);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }

        if (petition.getCurrentStatus() == PetitionStatus.Status.IN_PROGRESS) {
            ModelAndView modelAndView = new ModelAndView("petitions_resolve");
            modelAndView.addObject("action", action);
            modelAndView.addObject("pid", pid);
            modelAndView.addObject("pEmail", petition.getPetitioner().getEmail());
            modelAndView.addObject("attachmentApiUrl", "/api/petitions/" + pid + "/attachments");
            modelAndView.addObject("linkedPetitionsApiUrl", "/api/petitions/" + pid + "/linked");
            modelAndView.addObject("linkedPetitionerApiUrl", "/api/petitions/" + pid + "/by/petitioner");
            //TODO: catch exceptions, add  error message
            modelAndView.addObject("templateList", emailTemplateService.findByCategory(EmailTemplate.Category.response));
            return modelAndView;
        } else {
            ModelAndView modelAndView = new ModelAndView("redirect:/petition/" + petition.getId());
        	attr.addFlashAttribute("toast", i18nToast("controller.petition.resolve_work_not_started", ToastType.danger));
            return modelAndView;
        }
    }

    @RequestMapping(value = "/petition/{pid}/resolve/{action}", method = RequestMethod.POST)
    @ResponseBody
    public ModelAndView resolvePetition(@PathVariable("pid") long id,
                                        @PathVariable("action") String action,
                                        @RequestParam("resolution") PetitionStatus.Resolution resolution,
                                        @RequestParam("email") boolean sendEmail,
                                        @RequestParam(value = "attachments[]", required = false) Long[] attachments,
                                        @RequestParam("description") String description,
                                        final RedirectAttributes attr) {

        ModelAndView modelAndView = new ModelAndView();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        //TODO: catch exceptions, add  error message
        User user = userService.findUserByEmail(auth.getName()).get(0);
        Petition petition = petitionService.findById(id);
        modelAndView.setViewName("redirect:/petition/" + petition.getId());

        ValidationStatus validationStatus = validateSolutionParameters(petition, resolution, sendEmail, description);

        if (!validationStatus.isValid()) {
            attr.addFlashAttribute("toast", i18nToast(validationStatus.getMsg(), ToastType.danger));
            modelAndView.setViewName("redirect:/petition/" + id + "/resolve/" + action);
        } else {
            PetitionStatus.Status status = resolution.getStatus();
            statusService.create(status, petition, user);

            if (sendEmail) {
                try {
                    smtpService.send(createEmail("Soluționare petiție: " + i18n(resolution), description,
                                                 petition.getPetitioner().getEmail(), attachments, petition));
                	attr.addFlashAttribute("toast", i18nToast("controller.petition.resolve_successful", ToastType.success));
                } catch (MessagingException e) {
                	attr.addFlashAttribute("toast", i18nToast("controller.petition.resolve_failed", ToastType.danger, e.getMessage()));
                    LOGGER.debug(i18n("controller.petition.resolve_failed"), e);
                }
            } else {
            	String message = i18n("controller.petition.resolve_resolution") + ": " + i18n(resolution) + " \n <br/> " + description;
            	//TODO: catch exceptions, add  error message
            	commentService.createAndSave(user, petition, message );
            	attr.addFlashAttribute("toast", i18nToast("controller.petition.resolve_successful", ToastType.success));
            }
        }

        return modelAndView;
    }

    private void addCustomParams(ModelAndView modelAndView) {
        modelAndView.addObject("user_list", userService.getAllUsers());

        for (PetitionCustomParam.Type type : PetitionCustomParam.Type.values()) {
        	//TODO: catch exceptions, add  error message
        	PetitionCustomParam param = petitionCustomParamService.findByType(type);
            modelAndView.addObject(type.name(), param);
        }
    }

    private ValidationStatus validateSolutionParameters(Petition petition, PetitionStatus.Resolution resolution,
                                                        boolean sendEmail, String description) {
        if (petition.getCurrentStatus() != PetitionStatus.Status.IN_PROGRESS) {
        	return new ValidationStatus(false, "controller.petition.resolve_work_not_started");
        }

        if (sendEmail && (description == null || description.trim().isEmpty())) {
        	return new ValidationStatus(false, "controller.petition.resolve_message_required");
        }

        if (Objects.equals(resolution, PetitionStatus.Resolution.duplicate) && petitionService.countLinkedPetitions(petition) == 0) {
        	return new ValidationStatus(false, "controller.petition.resolve_duplicate_petition");
        }

        return new ValidationStatus(true, null);
    }

    private Email createEmail(String subject, String description, String recipients, Long[] attachments, Petition petition) {
        List<Attachment> attachmentList = attachmentService.findByIds(attachments);

        Email email = new Email();
        email.setBody(description);
        email.setDate(new Date());
        email.setSubject(subject);
        email.setSender(smtpConfig.getUsername());
        email.setRecipients(recipients);
        email.setAttachments(attachmentList);
        email.setPetition(petition);
        email.setType(Email.EmailType.Outbox);
        //TODO: catch exceptions, add  error message
    	return emailService.save(email);
    }

    private String convertRecipients(long[] recipients) {
        StringBuilder recipientString = new StringBuilder("");
        for (long rid : recipients) {
            Contact contact = contactService.getById(rid);
            recipientString.append(contact.getName()).append(" <").append(contact.getEmail()).append(">, ");
        }
        return recipientString.toString();
    }
}