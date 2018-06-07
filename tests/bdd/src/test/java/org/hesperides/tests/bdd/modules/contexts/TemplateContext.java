package org.hesperides.tests.bdd.modules.contexts;

import cucumber.api.java8.En;
import org.hesperides.presentation.io.TemplateIO;
import org.hesperides.tests.bdd.CucumberSpringBean;
import org.hesperides.tests.bdd.templatecontainer.TemplateSamples;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;

public class TemplateContext extends CucumberSpringBean implements En {

    @Autowired
    private ModuleContext moduleContext;

    public TemplateContext() {
        Given("^an existing template in this module$", () -> {
            addTemplateToExistingModule();
        });
    }

    public ResponseEntity<TemplateIO> retrieveExistingTemplate() {
        return retrieveExistingTemplate(TemplateSamples.DEFAULT_NAME);
    }

    private ResponseEntity<TemplateIO> retrieveExistingTemplate(String name) {
        return rest.getTestRest().getForEntity(getTemplateURI(name), TemplateIO.class);
    }

    public ResponseEntity<TemplateIO> addTemplateToExistingModule() {
        TemplateIO templateInput = TemplateSamples.getTemplateInputWithDefaultValues();
        return addTemplateToExistingModule(templateInput);
    }

    public ResponseEntity<TemplateIO> addTemplateToExistingModule(String templateName) {
        TemplateIO templateInput = TemplateSamples.getTemplateInputWithName(templateName);
        return addTemplateToExistingModule(templateInput);
    }

    private ResponseEntity<TemplateIO> addTemplateToExistingModule(TemplateIO templateInput) {
        ResponseEntity<TemplateIO> response = rest.getTestRest().postForEntity(getTemplatesURI(), templateInput, TemplateIO.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response;
    }

    // URIs

    public String getTemplatesURI() {
        return moduleContext.getModuleURI() + "/templates";
    }

    public String getTemplateURI(String templateName) {
        return String.format(getTemplatesURI() + "/" + templateName);
    }
}
