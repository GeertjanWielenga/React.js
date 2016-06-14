@TemplateRegistrations({
    @TemplateRegistration(
        folder = "ClientSide",
        content = "ReactBasicComponent.js",
        requireProject = false,
        displayName = "React Basic Component",
        iconBase = "org/netbeans/modules/react/react/file/react.png"
    ),
    @TemplateRegistration(
        folder = "ClientSide",
        content = "ReactStatelessComponent.js",
        requireProject = false,
        displayName = "React Stateless Component",
        iconBase = "org/netbeans/modules/react/react/file/react.png"
    )
})
package org.netbeans.modules.react.react.templates;

import org.netbeans.api.templates.TemplateRegistrations;
import org.netbeans.api.templates.TemplateRegistration;