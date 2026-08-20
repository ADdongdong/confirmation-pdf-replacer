import React, { useCallback, useEffect, useRef, useState } from 'react';

/**
 * PDF 预览遮罩拖拽组件
 * 支持两个可拖拽遮罩：
 *   - 上界覆盖区（蓝色）：从顶部到 value，表示头部白化重写区域
 *   - 下界页脚区（蓝色，可选）：从 footerY 到页面底部，表示页码行遮盖区域
 * @param {object}  props
 * @param {string}  props.imageUrl       预览图地址
 * @param {number}  props.pageHeight     页面高度（pt）
 * @param {number}  props.tableY         表格保护线位置（pt，可选）
 * @param {string}  props.unit           'pct'（百分比）或 'pt'
 * @param {number}  props.autoValue      自动值
 * @param {number}  props.value          当前值
 * @param {boolean} props.force          是否强制使用当前值（false=自动）
 * @param {function} props.onValueChange 值变化回调(newValue)
 * @param {function} props.onForceChange 强制状态变化回调
 * @param {function} props.onReset       重置为自动
 * @param {number}  props.autoFooterY    页脚自动值（从顶部算起 y，可选）
 * @param {number}  props.footerY        页脚当前值（从顶部算起 y，可选）
 * @param {boolean} props.footerForce    是否强制页脚值
 * @param {function} props.onFooterChange 页脚值变化回调(footerY)
 * @param {function} props.onFooterForceChange 页脚强制状态变化回调
 * @param {function} props.onFooterReset 页脚重置为自动
 */
export default function PreviewOverlay({
  imageUrl,
  pageHeight,
  tableY,
  unit = 'pt',
  autoValue,
  value,
  force = false,
  onValueChange,
  onForceChange,
  onReset,
  autoFooterY,
  footerY,
  footerForce = false,
  onFooterChange,
  onFooterForceChange,
  onFooterReset,
}) {
  const imgRef = useRef(null);
  const [imgHeight, setImgHeight] = useState(0);
  const dragRef = useRef(null);
  const footerDragRef = useRef(null);

  const hasFooter = typeof footerY === 'number' && typeof onFooterChange === 'function';

  // 测量图片实际渲染高度：图片加载完成、窗口缩放、容器尺寸变化时都重新测量
  useEffect(() => {
    const img = imgRef.current;
    if (!img) return;
    const update = () => {
      const h = img.clientHeight || img.offsetHeight;
      if (h > 0) setImgHeight(h);
    };

    if (img.complete && img.naturalWidth > 0) {
      update();
    }

    const onLoad = () => update();
    img.addEventListener('load', onLoad);
    window.addEventListener('resize', update);

    let observer = null;
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(update);
      observer.observe(img);
    }

    return () => {
      img.removeEventListener('load', onLoad);
      window.removeEventListener('resize', update);
      if (observer) observer.disconnect();
    };
  }, [imageUrl]);

  const clamp = useCallback(
    (v) => {
      if (unit === 'pct') {
        return Math.max(5, Math.min(75, v));
      }
      const max = tableY ? tableY - 5 : 75;
      return Math.max(48, Math.min(max, v));
    },
    [unit, tableY]
  );

  const clampFooter = useCallback(
    (y) => {
      // 页脚遮盖带合理范围：footerHeight 8pt ~ 60pt
      // 对应 y 范围：[pageHeight - 60, pageHeight - 8]
      // 但要避免与上界覆盖重叠（footer y 必须 > value + 10）
      const maxFooterHeight = 60;
      const minFooterHeight = 8;
      const lower = Math.max(value + 10, pageHeight - maxFooterHeight);
      const upper = pageHeight - minFooterHeight;
      return Math.max(lower, Math.min(upper, y));
    },
    [value, pageHeight]
  );

  const onMouseDown = (e) => {
    e.preventDefault();
    dragRef.current = { startY: e.clientY, startValue: value };
    const onMove = (ev) => {
      if (!dragRef.current) return;
      const delta = ((ev.clientY - dragRef.current.startY) / imgHeight) * pageHeight;
      let newVal;
      if (unit === 'pct') {
        newVal = (dragRef.current.startValue / 100) * pageHeight + delta;
        newVal = clamp((newVal / pageHeight) * 100);
      } else {
        newVal = clamp(dragRef.current.startValue + delta);
      }
      onValueChange(Math.round(newVal * 10) / 10);
    };
    const onUp = () => {
      dragRef.current = null;
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  };

  const onTouchStart = (e) => {
    e.preventDefault();
    const t = e.touches[0];
    dragRef.current = { startY: t.clientY, startValue: value };
    const onMove = (ev) => {
      if (!dragRef.current) return;
      const delta = ((ev.touches[0].clientY - dragRef.current.startY) / imgHeight) * pageHeight;
      let newVal;
      if (unit === 'pct') {
        newVal = (dragRef.current.startValue / 100) * pageHeight + delta;
        newVal = clamp((newVal / pageHeight) * 100);
      } else {
        newVal = clamp(dragRef.current.startValue + delta);
      }
      onValueChange(Math.round(newVal * 10) / 10);
    };
    const onUp = () => {
      dragRef.current = null;
      document.removeEventListener('touchmove', onMove);
      document.removeEventListener('touchend', onUp);
    };
    document.addEventListener('touchmove', onMove, { passive: false });
    document.addEventListener('touchend', onUp);
  };

  // ===== 页脚下界遮罩拖拽 =====
  const onFooterMouseDown = (e) => {
    e.preventDefault();
    footerDragRef.current = { startY: e.clientY, startValue: footerY };
    const onMove = (ev) => {
      if (!footerDragRef.current) return;
      const delta = ((ev.clientY - footerDragRef.current.startY) / imgHeight) * pageHeight;
      onFooterChange(Math.round(clampFooter(footerDragRef.current.startValue + delta) * 10) / 10);
    };
    const onUp = () => {
      footerDragRef.current = null;
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  };

  const onFooterTouchStart = (e) => {
    e.preventDefault();
    const t = e.touches[0];
    footerDragRef.current = { startY: t.clientY, startValue: footerY };
    const onMove = (ev) => {
      if (!footerDragRef.current) return;
      const delta = ((ev.touches[0].clientY - footerDragRef.current.startY) / imgHeight) * pageHeight;
      onFooterChange(Math.round(clampFooter(footerDragRef.current.startValue + delta) * 10) / 10);
    };
    const onUp = () => {
      footerDragRef.current = null;
      document.removeEventListener('touchmove', onMove);
      document.removeEventListener('touchend', onUp);
    };
    document.addEventListener('touchmove', onMove, { passive: false });
    document.addEventListener('touchend', onUp);
  };

  const overlayPx = imgHeight ? (value / pageHeight) * imgHeight : 0;
  const footerPx = imgHeight && hasFooter ? (footerY / pageHeight) * imgHeight : 0;
  const tablePx = imgHeight && tableY ? (tableY / pageHeight) * imgHeight : 0;
  const footerHeightPt = hasFooter ? pageHeight - footerY : 0;

  // 按需求只展示当前值，不再区分"自动/手动"
  const labelText =
    unit === 'pct'
      ? Math.round((value / 100) * pageHeight) + ' pt'
      : Math.round(value) + ' pt';

  return (
    <div style={{ marginTop: 12 }}>
      <div style={{ position: 'relative', display: 'inline-block', maxWidth: '100%', background: '#fff', borderRadius: 8, boxShadow: '0 2px 8px rgba(0,0,0,0.08)', overflow: 'hidden' }}>
        <img
          ref={imgRef}
          src={imageUrl}
          alt="PDF预览"
          style={{ display: 'block', maxWidth: '100%', height: 'auto' }}
        />
        {imgHeight > 0 && (
          <>
            {/* 上界覆盖区（蓝色半透明，头部白化区域） */}
            <div
              className="preview-overlay"
              style={{ height: overlayPx, borderBottomColor: '#2563eb' }}
            />
            <div
              className="drag-handle"
              onMouseDown={onMouseDown}
              onTouchStart={onTouchStart}
              style={{ top: overlayPx }}
              title="拖动调整覆盖下界"
            >
              ≡
            </div>
            <div className="overlay-label" style={{ top: overlayPx, right: 8, left: 'auto', background: 'rgba(37, 99, 235, 0.85)' }}>
              {Math.round(value)} {unit === 'pct' ? '%' : 'pt'}
            </div>

            {/* 表格保护线（绿色）：按需求隐藏 */}
            {false && tableY && (
              <div
                className="legend-bar show"
                style={{ top: tablePx, background: '#22c55e' }}
                title={'表格保护线 y=' + Math.round(tableY) + 'pt'}
              />
            )}

            {/* 下界页脚区（蓝色半透明，页码行遮盖区域） */}
            {hasFooter && (
              <>
                <div
                  className="preview-overlay"
                  style={{
                    top: footerPx,
                    bottom: 0,
                    height: 'auto',
                    background: 'rgba(59, 130, 246, 0.28)',
                    borderTop: '3px solid #2563eb',
                    borderBottom: 'none',
                  }}
                />
                <div
                  className="drag-handle"
                  onMouseDown={onFooterMouseDown}
                  onTouchStart={onFooterTouchStart}
                  style={{ top: footerPx, background: 'rgba(37, 99, 235, 0.6)' }}
                  title="拖动调整页脚遮盖上边界"
                >
                  ≡
                </div>
                <div
                  className="overlay-label"
                  style={{ top: footerPx, right: 8, left: 'auto', background: 'rgba(37, 99, 235, 0.85)', transform: 'translateY(-120%)' }}
                >
                  页脚 {Math.round(footerHeightPt)}pt
                </div>
              </>
            )}
          </>
        )}
      </div>

      <div style={{ marginTop: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12, flexWrap: 'wrap' }}>
        <span style={{ fontSize: 13, color: '#2563eb' }}>■ 上界覆盖：{Math.round(value)} pt</span>
        {hasFooter ? (
          <span style={{ fontSize: 13, color: '#2563eb' }}>
            ■ 下界页脚：{Math.round(footerHeightPt)} pt
          </span>
        ) : null}
        <button className="btn-sm" onClick={onReset} type="button">
          重置上界
        </button>
        <button
          className={force ? 'btn-sm active' : 'btn-sm'}
          onClick={() => onForceChange(!force)}
          type="button"
        >
          {force ? '使用当前值' : '使用当前值'}
        </button>
        {hasFooter && (
          <>
            <button className="btn-sm" onClick={onFooterReset} type="button">
              重置页脚
            </button>
            <button
              className={footerForce ? 'btn-sm active' : 'btn-sm'}
              onClick={() => onFooterForceChange(!footerForce)}
              type="button"
            >
              使用当前值
            </button>
          </>
        )}
      </div>
    </div>
  );
}
