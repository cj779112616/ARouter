package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.utils.Consts;
import com.alibaba.android.arouter.compiler.utils.Logger;
import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.ISYRINGE;
import static com.alibaba.android.arouter.compiler.utils.Consts.KEY_MODULE_NAME;
import static com.alibaba.android.arouter.compiler.utils.Consts.METHOD_INJECT;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.WARNING_TIPS;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Processor used to create autowired helper
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/20 下午5:56
 */
@AutoService(Processor.class)
@SupportedOptions(KEY_MODULE_NAME)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes({ANNOTATION_TYPE_AUTOWIRED})
public class AutowiredProcessor extends AbstractProcessor {
    private Filer mFiler;       // File util, write class file into disk.
    private Logger logger;
    private Types typeUtil;
    private Elements elementUtil;
    private Map<TypeElement, List<Element>> fatherAndChild = new HashMap<>();   // Contain field need autowired and his super class.

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        mFiler = processingEnv.getFiler();                  // Generate class.
        typeUtil = processingEnv.getTypeUtils();            // Get type utils.
        elementUtil = processingEnv.getElementUtils();      // Get class meta.
        logger = new Logger(processingEnv.getMessager());   // Package the log utils.

        logger.info(">>> AutowiredProcessor init. <<<");
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (CollectionUtils.isNotEmpty(set)) {
            try {
                logger.info(">>> Found autowired field, start... <<<");
                categories(roundEnvironment.getElementsAnnotatedWith(Autowired.class));
                generateHelper();

            } catch (Exception e) {
                logger.error(e);
            }
            return true;
        }

        return false;
    }

    private void generateHelper() throws IOException {
        TypeElement type_ISyringe = elementUtil.getTypeElement(ISYRINGE);
        TypeMirror iProvider = elementUtil.getTypeElement(Consts.IPROVIDER).asType();
        ;

        // Build input param name.
        ParameterSpec objectParamSpec = ParameterSpec.builder(TypeName.OBJECT, "target").build();

        // Build method : 'inject'
        MethodSpec.Builder injectMethod = MethodSpec.methodBuilder(METHOD_INJECT)
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(objectParamSpec);

        if (MapUtils.isNotEmpty(fatherAndChild)) {
            for (Map.Entry<TypeElement, List<Element>> entry : fatherAndChild.entrySet()) {
                TypeElement father = entry.getKey();
                List<Element> childs = entry.getValue();

                String qualifiedName = father.getQualifiedName().toString();
                String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
                String fileName = father.getSimpleName() + NAME_OF_AUTOWIRED;

                /*
                    ((MainActivity) target).helloService = ARouter.getInstance().navigation(HelloService.class);
                    if (((MainActivity) target).helloService == null) {
                        throw new RuntimeException("helloService is null, in class 'MainActivity L12'!");
                    }
                    ((MainActivity) target).helloService2 = (HelloService) ARouter.getInstance().build("/service/hello").navigation();
                    ((MainActivity) target).name = ((MainActivity) target).getIntent().getStringExtra("name");
                    ((MainActivity) target).boy = ((MainActivity) target).getIntent().getBooleanExtra("sex", false);
                 */

                // Generate method body, start inject.
                for (Element element : childs) {
                    if (typeUtil.isSubtype(element.asType(), iProvider)) {  // It's provider

                    } else {    // It's normal intent value

                    }
                }

                // Generate autowired helper
                JavaFile.builder(packageName,
                        TypeSpec.classBuilder(fileName)
                                .addJavadoc(WARNING_TIPS)
                                .addSuperinterface(ClassName.get(type_ISyringe))
                                .addModifiers(PUBLIC)
                                .addMethod(injectMethod.build())
                                .build()
                ).build().writeTo(mFiler);
            }
        }
    }

    /**
     * Categories field, find his papa.
     *
     * @param elements Field need autowired
     */
    private void categories(Set<? extends Element> elements) throws IllegalAccessException {
        if (CollectionUtils.isNotEmpty(elements)) {
            for (Element element : elements) {
                TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

                if (element.getModifiers().contains(Modifier.PRIVATE)) {
                    throw new IllegalAccessException("The autowired fields CAN NOT BE 'private'!!! please check field ["
                            + element.getSimpleName() + "] in class [" + enclosingElement.getQualifiedName() + "]");
                }

                if (fatherAndChild.containsKey(enclosingElement)) { // Has categries
                    fatherAndChild.get(enclosingElement).add(element);
                } else {
                    fatherAndChild.put(enclosingElement, new ArrayList<Element>() {{
                        add(element);
                    }});
                }
            }

            logger.info("categories finished.");

        }
    }
}
