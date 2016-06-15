@TemplateRegistrations({
    @TemplateRegistration(
        folder = "ClientSide", 
        content = "JSXTemplate.jsx", 
        requireProject = false, 
        description = "JSXDescription.html",
        displayName = "JSX File"
    ),
    @TemplateRegistration(
        folder = "ClientSide",
        content = "ReactBasicComponent.js",
        requireProject = false,
        displayName = "React Basic Component",
        iconBase = "org/netbeans/modules/react/react/file/react.png",
        scriptEngine = "freemarker"
    ),
    @TemplateRegistration(
        folder = "ClientSide",
        content = "ReactStatelessComponent.js",
        requireProject = false,
        displayName = "React Stateless Component",
        iconBase = "org/netbeans/modules/react/react/file/react.png",
        scriptEngine = "freemarker"
    )
})
package org.netbeans.modules.react.react.file;

import org.netbeans.api.templates.TemplateRegistration;
import org.netbeans.api.templates.TemplateRegistrations;
