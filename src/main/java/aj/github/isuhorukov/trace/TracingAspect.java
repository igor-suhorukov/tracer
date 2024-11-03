package aj.github.isuhorukov.trace;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.SourceLocation;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Consumer;

@Aspect
public class TracingAspect {
    private final Tracer tracer = GlobalOpenTelemetry.get().getTracer(TracingAspect.class.getName());

    @Pointcut("@annotation(org.junit.jupiter.api.Test)")
    public void withTestAnnotation() {
        //pointcut body, should be empty
    }

    @Pointcut("@annotation(io.qameta.allure.Step)")
    public void withStepAnnotation() {
        //pointcut body, should be empty
    }

    @Around(" !execution(public * get*()) && !execution(public void set*(*)) " +
            "&& !execution(public int hashCode()) " +
            "&& !execution(public java.lang.String toString()) " +
            "&& !execution(public boolean equals(java.lang.Object)) "
            +"&& (execution(public * ru..*(..)) || execution(public static * ru..*(..)))"
    )
    public Object aroundAnyMethodsFromProject(ProceedingJoinPoint pjp) throws Throwable {
        return proceedWithSpan(pjp, pjp.getSignature().toLongString(), result -> {
/*
            Span span = result.getKey();
            final String[] parameterNames = ((CodeSignature) pjp.getSignature()).getParameterNames();
            Object[] args = pjp.getArgs();
            for (int i = 0; i < Math.max(parameterNames.length, args.length); i++) {
                span.setAttribute("arg: "+parameterNames[i], String.valueOf(args[i]));
            }
            if(result.getValue()!=null){
                span.setAttribute("result", String.valueOf(result.getValue()));
            }
*/
        }, e -> {});
    }

    private Object proceedWithSpan(ProceedingJoinPoint pjp, String spanName,
                                   Consumer<Map.Entry<Span, Object>> successHandler,
                                   Consumer<Map.Entry<Span, Exception>> errorHandler) throws Throwable {
        SourceLocation location = pjp.getStaticPart().getSourceLocation();
        if(location.getLine()==0 || Boolean.getBoolean("skipTracing")){ //auto generated proxy, lombok magic etc
            return pjp.proceed();
        }
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName);
        Span span = spanBuilder.setSpanKind(SpanKind.INTERNAL).startSpan();
        try(Scope scope = span.makeCurrent())  {
            Object proceed = pjp.proceed();
            span.setAttribute("srcCodeLine", location.getLine()-1);
            span.setAttribute("fqClass", pjp.getSignature().getDeclaringType().getName());
            successHandler.accept(new AbstractMap.SimpleImmutableEntry<>(span, proceed));
            return proceed;
        } catch (Exception ex) {
            errorHandler.accept(new AbstractMap.SimpleImmutableEntry<>(span, ex));
            span.recordException(ex);
            throw ex;
        }
        finally {
            span.end();
        }
    }

    private static String getSpanName(ProceedingJoinPoint pjp, String descriptionText) {
        return descriptionText != null ? descriptionText : pjp.getSignature().getName();
    }
}
