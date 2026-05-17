package com.minelittlepony.ponynpc.client.render;

import com.minelittlepony.ponynpc.entity.QuestMarkerEntity;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Рендерер маркера квеста:
 * — Если задан display item → вращающийся предмет на высоте ~0.9 блока.
 * — Иначе → вращающийся золотой каркасный куб.
 */
public class QuestMarkerRenderer extends EntityRenderer<QuestMarkerEntity> {

    private static final float R = 0.831f, G = 0.627f, B = 0.090f;
    private static final float HALF = 0.22f;

    private final net.minecraft.client.render.item.ItemRenderer itemRenderer;

    public QuestMarkerRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.25f;
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(QuestMarkerEntity entity, float yaw, float delta,
                       MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        matrices.push();

        float angle = ((entity.age + delta) * 1.8f) % 360f;

        ItemStack displayItem = entity.getDisplayItem();
        if (!displayItem.isEmpty()) {
            // Вращающийся предмет
            matrices.translate(0.0, 0.9, 0.0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            matrices.scale(0.6f, 0.6f, 0.6f);
            itemRenderer.renderItem(displayItem, ModelTransformationMode.GROUND,
                light, OverlayTexture.DEFAULT_UV, matrices, vertices, entity.getWorld(), entity.getId());
        } else {
            // Вращающийся золотой каркасный куб
            matrices.translate(0.0, 0.8, 0.0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(20f));
            VertexConsumer lines = vertices.getBuffer(RenderLayer.getLines());
            WorldRenderer.drawBox(matrices, lines,
                -HALF, -HALF, -HALF, HALF, HALF, HALF,
                R, G, B, 1f);
        }

        matrices.pop();

        super.render(entity, yaw, delta, matrices, vertices, light);
    }

    @Override
    public Identifier getTexture(QuestMarkerEntity entity) {
        return Identifier.of("minecraft", "textures/misc/white.png");
    }
}
