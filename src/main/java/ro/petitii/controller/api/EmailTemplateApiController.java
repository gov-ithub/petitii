package ro.petitii.controller.api;

import com.fasterxml.jackson.annotation.JsonView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import ro.petitii.controller.BaseController;
import ro.petitii.model.EmailTemplate;
import ro.petitii.model.Petition;
import ro.petitii.service.EmailTemplateService;
import ro.petitii.service.PetitionService;
import ro.petitii.service.template.EmailTemplateProcessorService;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
public class EmailTemplateApiController extends BaseController{
    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private EmailTemplateProcessorService emailTemplateProcessorService;

    @Autowired
    private PetitionService petitionService;

    @JsonView(DataTablesOutput.View.class)
    @PreAuthorize("hasAuthority('ADMIN')")
    @RequestMapping(value = "/api/emailTemplates", method = {RequestMethod.GET, RequestMethod.POST})
    public DataTablesOutput<EmailTemplate> getEmailTemplates(@Valid DataTablesInput input) {
    	//TODO: catch exceptions, add  error/success message
        return emailTemplateService.findAll(input);
    }

    /**
     * this action has to be available to all users
     */
    @ResponseBody
    @RequestMapping(value = "/api/emailTemplate/{tid}/petition/{pid}", method = RequestMethod.GET)
    public String compileByPetition(@PathVariable("tid") Long tid, @PathVariable("pid") Long pid) {
    	//TODO: catch exceptions, add  error/success message
        Petition petition = petitionService.findById(pid);
        if (petition == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("pet", petition);
        variables.put("petition", petition);

        return emailTemplateProcessorService.processTemplateWithId(tid, variables);
    }
}