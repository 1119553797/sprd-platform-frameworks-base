package android.widget;

import android.content.Context;

/**
 * {@hide}
 */
public abstract class AbsLockScreen extends RelativeLayout implements
		ILockScreen {

	/**
	 * {@hide}
	 * @param context
	 */
    public AbsLockScreen(Context context, ILockScreenListener listener) {
        super(context);
        mLockScreenListener = listener;
    }

	protected ILockScreenListener mLockScreenListener;

}