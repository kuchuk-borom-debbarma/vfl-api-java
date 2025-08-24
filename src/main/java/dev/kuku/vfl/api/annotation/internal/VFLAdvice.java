package dev.kuku.vfl.api.annotation.internal;

import dev.kuku.vfl.api.VFLInitializer;
import dev.kuku.vfl.internal.dto.BlockContext;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeReferencedBlock;
import dev.kuku.vfl.api.dataProvider.VFLAnnotationDataProvider;
import dev.kuku.vfl.internal.dataProvider.VFLDataProvider;
import dev.kuku.vfl.internal.util.FlowUtil;
import net.bytebuddy.asm.Advice;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public final class VFLAdvice {
    public static final Logger log = LoggerFactory.getLogger(VFLAdvice.class);
    public static final @MonotonicNonNull VFLDataProvider PROVIDER = VFLInitializer.DATA_PROVIDER;

    private VFLAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onSubBlockEntered(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
        if (PROVIDER == null) {
            log.error("VFLInitializer.Init() has not been invoked! Please initialize VFL before using it.");
            return;
        }
        if (!(PROVIDER instanceof VFLAnnotationDataProvider)) {
            log.error("VFL Initializer's VFLData dataProvider is NOT of type {}. It is mandatory to use {} for @{} to function.",
                    VFLAnnotationDataProvider.class.getSimpleName(),
                    VFLAnnotationDataProvider.class.getSimpleName(),
                    dev.kuku.vfl.api.annotation.SubBlock.class.getSimpleName());
            return;
        }
        VFLAnnotationDataProvider annotationDataProvider = (VFLAnnotationDataProvider) PROVIDER;
        BlockContext parentContext = PROVIDER.getBlockContext();
        if (parentContext == null) {
            log.warn("No parent block context found for method: {}. Cannot create sub-block", method.getName());
            return;
        }
        Block subBlock = FlowUtil.CreateBlockAndPushToBuffer(method.getName());
        if (subBlock == null) {
            log.error("Failed to push sub-block to buffer for method: {}", method.getName());
            return;
        }
        BlockLog subBlockStartLog = FlowUtil.CreateLogForContextAndPush2Buffer("Starting sub block: " + method.getName(), subBlock.getId(), LogTypeReferencedBlock.PRIMARY_BLOCK_START);
        if (subBlockStartLog == null) {
            log.error("Failed to push sub-block start to buffer for subBlock {} in method: {}", subBlock.getId(), method.getName());
            return;
        }
        parentContext.setCurrentLogId(subBlockStartLog.getId());
        FlowUtil.PushBlockEnteredToBuffer(subBlock.getId());
        BlockContext subBlockContext = new BlockContext(subBlock);
        annotationDataProvider.pushBlockContextToThreadLocalStack(subBlockContext);
    }

    public static void onSubBlockExited(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
        if (PROVIDER == null) {
            log.error("VFLInitializer.Init() has not been invoked! Please initialize VFL before using it.");
            return;
        }
        if (!(PROVIDER instanceof VFLAnnotationDataProvider)) {
            log.error("VFL Initializer's VFLData dataProvider is NOT of type {}. It is mandatory to use {} for @{} to function.",
                    VFLAnnotationDataProvider.class.getSimpleName(),
                    VFLAnnotationDataProvider.class.getSimpleName(),
                    dev.kuku.vfl.api.annotation.SubBlock.class.getSimpleName());
            return;
        }
        VFLAnnotationDataProvider annotationDataProvider = (VFLAnnotationDataProvider) PROVIDER;
        BlockContext parentContext = PROVIDER.getBlockContext();
        if (parentContext == null) {
            log.warn("No parent block context found for method: {}. Cannot create sub-block", method.getName());
            return;
        }
        annotationDataProvider.popLatestBlockContextFromThreadLocalStack();
    }
}
