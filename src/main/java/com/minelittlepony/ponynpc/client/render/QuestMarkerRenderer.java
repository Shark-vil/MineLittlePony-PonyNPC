package com.minelittlepony.ponynpc.client.render;

import com.minelittlepony.ponynpc.entity.QuestMarkerEntity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Рендерер маркера квеста:
 * — Вращающийся золотой каркасный куб на высоте ~0.8 блока.
 * — Плашка с именем маркера (через стандартный EntityRenderer).
 */
public class QuestMarkerRenderer extends EntityRenderer<QuestMarkerEntity> {

    // Тот же золотой цвет что и в UI (C_GOLD = 0xFFD4A017)
    private static final float R = 0.831f, G = 0.627f, B = 0.090f;
    private static final float HALF = 0.22f;

    public QuestMarkerRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.25f;
    }

    @Override
    public void render(QuestMarkerEntity entity, float yaw, float delta,
                       MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        matrices.push();

        // Поднимаем куб над землёй
        matrices.translate(0.0, 0.8, 0.0);

        // Медленное вращение
        float angle = ((entity.age + delta) * 1.8f) % 360f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(20f));

        VertexConsumer lines = vertices.getBuffer(RenderLayer.getLines());
        WorldRenderer.drawBox(matrices, lines,
            -HALF, -HALF, -HALF, HALF, HALF, HALF,
            R, G, B, 1f);

        matrices.pop();

        // Плашка с именем (стандартный nameplate EntityRenderer)
        super.render(entity, yaw, delta, matrices, vertices, light);
    }

    @Override
    public Identifier getTexture(QuestMarkerEntity entity) {
        // Маркер не использует текстуру — возвращаем заглушку
        return Identifier.of("minecraft", "textures/misc/white.png");
    }
}
