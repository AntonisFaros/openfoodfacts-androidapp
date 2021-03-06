package openfoodfacts.github.scrachx.openfood.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.hootsuite.nachos.NachoTextView;
import com.hootsuite.nachos.validator.ChipifyingNachoValidator;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.theartofdev.edmodo.cropper.CropImage;
import openfoodfacts.github.scrachx.openfood.utils.LocaleHelper;
import org.greenrobot.greendao.async.AsyncSession;

import java.io.File;
import java.util.*;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import butterknife.OnTextChanged;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.models.*;
import openfoodfacts.github.scrachx.openfood.utils.Utils;
import openfoodfacts.github.scrachx.openfood.views.AddProductActivity;
import openfoodfacts.github.scrachx.openfood.views.FullScreenImageRotate;
import openfoodfacts.github.scrachx.openfood.views.OFFApplication;
import org.jsoup.helper.StringUtil;
import pl.aprilapps.easyphotopicker.DefaultCallback;
import pl.aprilapps.easyphotopicker.EasyImage;

import static android.Manifest.permission.CAMERA;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.hootsuite.nachos.terminator.ChipTerminatorHandler.BEHAVIOR_CHIPIFY_CURRENT_TOKEN;
import static openfoodfacts.github.scrachx.openfood.models.ProductImageField.INGREDIENTS;
import static openfoodfacts.github.scrachx.openfood.utils.Utils.MY_PERMISSIONS_REQUEST_CAMERA;

public class AddProductIngredientsFragment extends BaseFragment {
    private static final String PARAM_INGREDIENTS = "ingredients_text";
    private static final String PARAM_TRACES = "add_traces";
    private static final String PARAM_LANGUAGE = "lang";
    @BindView(R.id.btnAddImageIngredients)
    ImageView imageIngredients;
    @BindView(R.id.imageProgress)
    ProgressBar imageProgress;
    @BindView(R.id.imageProgressText)
    TextView imageProgressText;
    @BindView(R.id.ingredients_list)
    EditText ingredients;
    @BindView(R.id.btn_extract_ingredients)
    Button extractIngredients;
    @BindView(R.id.ocr_progress)
    ProgressBar ocrProgress;
    @BindView(R.id.ocr_progress_text)
    TextView ocrProgressText;
    @BindView(R.id.ingredients_list_verified)
    ImageView ingredientsVerifiedTick;
    @BindView(R.id.btn_looks_good)
    Button btnLooksGood;
    @BindView(R.id.btn_skip_ingredients)
    Button btnSkipIngredients;
    @BindView(R.id.traces)
    NachoTextView traces;
    @BindView(R.id.section_traces)
    TextView tracesHeader;
    @BindView(R.id.hint_traces)
    TextView tracesHint;
    @BindView(R.id.grey_line2)
    View greyLine2;
    AllergenNameDao mAllergenNameDao;
    private Activity activity;
    private File photoFile;
    private String code;
    private List<String> allergens = new ArrayList<>();
    private OfflineSavedProduct mOfflineSavedProduct;
    private HashMap<String, String> productDetails = new HashMap<>();
    private String imagePath;
    private boolean edit_product;
    private Product product;
    private boolean newImageSelected;

    private final int ROTATE_RESULT = 100;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_product_ingredients, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        extractIngredients.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_compare_arrows_black_18dp,0,0,0);
        if (getActivity().getIntent().getBooleanExtra(AddProductActivity.MODIFY_NUTRITION_PROMPT, false)) {
            if (!getActivity().getIntent().getBooleanExtra(AddProductActivity.MODIFY_CATEGORY_PROMPT, false)) {
                ((AddProductActivity) getActivity()).proceed();
            }
        }
        Bundle b = getArguments();
        if (b != null) {
            mAllergenNameDao = Utils.getAppDaoSession(activity).getAllergenNameDao();
            product = (Product) b.getSerializable("product");
            mOfflineSavedProduct = (OfflineSavedProduct) b.getSerializable("edit_offline_product");
            edit_product = b.getBoolean("edit_product");
            if (product != null) {
                code = product.getCode();
            }
            if (edit_product && product != null) {
                code = product.getCode();
                preFillProductValues();
            } else if (mOfflineSavedProduct != null) {
                code = mOfflineSavedProduct.getBarcode();
                preFillValues();
            } else {
                //addition
                if (PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("fastAdditionMode", false)) {
                    enableFastAdditionMode(true);
                } else {
                    enableFastAdditionMode(false);
                }
            }
            if (b.getBoolean("perform_ocr")) {
                extractIngredients();
            }
            if (b.getBoolean("send_updated")) {
                newIngredientsImage();
            }
        } else {
            Toast.makeText(activity, R.string.error_adding_ingredients, Toast.LENGTH_SHORT).show();
            activity.finish();
        }
        if (ingredients.getText().toString().isEmpty() && productDetails.get("image_ingredients") != null && !productDetails.get("image_ingredients").isEmpty()) {
            extractIngredients.setVisibility(View.VISIBLE);
            imagePath = productDetails.get("image_ingredients");
        } else if (edit_product && ingredients.getText().toString().isEmpty() && product.getImageIngredientsUrl() != null && !product.getImageIngredientsUrl().isEmpty()) {
            extractIngredients.setVisibility(View.VISIBLE);
        }
        loadAutoSuggestions();
        if (getActivity() instanceof AddProductActivity && ((AddProductActivity) getActivity()).getInitialValues() != null) {
            getAllDetails(((AddProductActivity) getActivity()).getInitialValues());
        }
    }

    /**
     * Pre fill the fields of the product which are already present on the server.
     */
    private void preFillProductValues() {
        if (product.getImageIngredientsUrl() != null && !product.getImageIngredientsUrl().isEmpty()) {
            imageProgress.setVisibility(View.VISIBLE);
            imagePath = product.getImageIngredientsUrl();
            Picasso.with(getContext())
                .load(product.getImageIngredientsUrl())
                .resize(dps50ToPixels(), dps50ToPixels())
                .centerInside()
                .into(imageIngredients, new Callback() {
                    @Override
                    public void onSuccess() {
                        imageProgress.setVisibility(View.GONE);
                    }

                    @Override
                    public void onError() {
                        imageProgress.setVisibility(View.GONE);
                    }
                });
        }
        if (product.getIngredientsText() != null && !product.getIngredientsText().isEmpty()) {
            ingredients.setText(product.getIngredientsText());
        }
        if (product.getTracesTags() != null && !product.getTracesTags().isEmpty()) {
            List<String> tracesTags = product.getTracesTags();
            final List<String> chipValues = new ArrayList<>();
            final String  appLanguageCode = LocaleHelper.getLanguage(activity);
            for (String tag : tracesTags) {
                chipValues.add(getTracesName(appLanguageCode, tag));
            }
            traces.setText(chipValues);
        }
    }

    private String getTracesName(String languageCode, String tag) {
        AllergenName allergenName = mAllergenNameDao.queryBuilder().where(AllergenNameDao.Properties.AllergenTag.eq(tag), AllergenNameDao.Properties.LanguageCode.eq(languageCode))
            .unique();
        if (allergenName != null) {
            return allergenName.getName();
        }
        return tag;
    }

    /**
     * To enable fast addition mode
     *
     * @param isEnabled
     */
    private void enableFastAdditionMode(boolean isEnabled) {
        if (isEnabled) {
            traces.setVisibility(View.GONE);
            tracesHeader.setVisibility(View.GONE);
            tracesHint.setVisibility(View.GONE);
            greyLine2.setVisibility(View.GONE);
        } else {
            traces.setVisibility(View.VISIBLE);
            tracesHeader.setVisibility(View.VISIBLE);
            tracesHint.setVisibility(View.VISIBLE);
            greyLine2.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Pre fill the fields if the product is already present in SavedProductOffline db.
     */
    private void preFillValues() {
        productDetails = mOfflineSavedProduct.getProductDetailsMap();
        if (productDetails != null) {
            if (productDetails.get("image_ingredients") != null) {
                imageProgress.setVisibility(View.VISIBLE);
                Picasso.with(getContext())
                    .load("file://" + productDetails.get("image_ingredients"))
                    .resize(dps50ToPixels(), dps50ToPixels())
                    .centerInside()
                    .into(imageIngredients, new Callback() {
                        @Override
                        public void onSuccess() {
                            imageProgress.setVisibility(View.GONE);
                        }

                        @Override
                        public void onError() {
                            imageProgress.setVisibility(View.GONE);
                        }
                    });
            }
            String lc = productDetails.get(PARAM_LANGUAGE) != null ? productDetails.get(PARAM_LANGUAGE) : "en";
            if (productDetails.get(PARAM_INGREDIENTS + "_" + lc) != null) {
                ingredients.setText(productDetails.get(PARAM_INGREDIENTS + "_" + lc));
            } else if (productDetails.get(PARAM_INGREDIENTS + "_" + "en") != null) {
                ingredients.setText(productDetails.get(PARAM_INGREDIENTS + "_" + "en"));
            }
            if (productDetails.get(PARAM_TRACES) != null) {
                List<String> chipValues = Arrays.asList(productDetails.get(PARAM_TRACES).split("\\s*,\\s*"));
                traces.setText(chipValues);
            }
        }
    }

    private void loadAutoSuggestions() {
        DaoSession daoSession = OFFApplication.getInstance().getDaoSession();
        AsyncSession asyncSessionAllergens = daoSession.startAsyncSession();
        AllergenNameDao allergenNameDao = daoSession.getAllergenNameDao();
        final String  appLanguageCode = LocaleHelper.getLanguage(activity);
        asyncSessionAllergens.queryList(allergenNameDao.queryBuilder()
            .where(AllergenNameDao.Properties.LanguageCode.eq(appLanguageCode))
            .orderDesc(AllergenNameDao.Properties.Name).build());

        asyncSessionAllergens.setListenerMainThread(operation -> {
            @SuppressWarnings("unchecked")
            List<AllergenName> allergenNames = (List<AllergenName>) operation.getResult();
            allergens.clear();
            for (int i = 0; i < allergenNames.size(); i++) {
                allergens.add(allergenNames.get(i).getName());
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, allergens);
            traces.addChipTerminator(',', BEHAVIOR_CHIPIFY_CURRENT_TOKEN);
            traces.setNachoValidator(new ChipifyingNachoValidator());
            traces.enableEditChipOnTouch(false, true);
            traces.setAdapter(adapter);
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = getActivity();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @OnClick(R.id.btnAddImageIngredients)
    void addIngredientsImage() {
        if (imagePath != null) {
            // ingredients image is already added. Open full screen image.
            Intent intent = new Intent(getActivity(), FullScreenImageRotate.class);
            Bundle bundle = new Bundle();
            if (edit_product && !newImageSelected) {
                bundle.putString("imageurl", imagePath);
            } else {
                bundle.putString("imageurl", "file://" + imagePath);
            }
            bundle.putString("code", product.getCode());
            bundle.putString("id", "ingredients_en");
            intent.putExtras(bundle);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ActivityOptionsCompat options = ActivityOptionsCompat.
                    makeSceneTransitionAnimation(activity, imageIngredients,
                        activity.getString(R.string.product_transition));
                startActivityForResult(intent, ROTATE_RESULT, options.toBundle());
            } else {
                startActivityForResult(intent, ROTATE_RESULT);
            }
        } else {
            // add ingredients image.
            if (ContextCompat.checkSelfPermission(activity, CAMERA) != PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
            } else {
                EasyImage.openCamera(this, 0);
            }
        }
    }

    @OnLongClick(R.id.btnAddImageIngredients)
    boolean newIngredientsImage() {
        if (ContextCompat.checkSelfPermission(activity, CAMERA) != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
        } else {
            EasyImage.openCamera(this, 0);
        }
        return true;
    }

    @OnClick(R.id.btn_next)
    void next() {
        Activity activity = getActivity();
        if (activity instanceof AddProductActivity) {
            ((AddProductActivity) activity).proceed();
        }
    }

    @OnClick(R.id.btn_looks_good)
    void ingredientsVerified() {
        ingredientsVerifiedTick.setVisibility(View.VISIBLE);
        traces.requestFocus();
        btnLooksGood.setVisibility(View.GONE);
        btnSkipIngredients.setVisibility(View.GONE);
    }

    @OnClick(R.id.btn_skip_ingredients)
    void skipIngredients() {
        ingredients.setText(null);
        btnSkipIngredients.setVisibility(View.GONE);
        btnLooksGood.setVisibility(View.GONE);
    }

    @OnClick(R.id.btn_extract_ingredients)
    void extractIngredients() {
        if (activity instanceof AddProductActivity) {
            if (imagePath != null && (!edit_product || newImageSelected)) {
                photoFile = new File(imagePath);
                ProductImage image = new ProductImage(code, INGREDIENTS, photoFile);
                image.setFilePath(imagePath);
                ((AddProductActivity) activity).addToPhotoMap(image, 1);
            } else if (imagePath != null) {
                ((AddProductActivity) activity).performOCR(code, "ingredients_" + ((AddProductActivity) activity).getProductLanguage());
            }
        }
    }

    @OnTextChanged(value = R.id.ingredients_list, callback = OnTextChanged.Callback.AFTER_TEXT_CHANGED)
    void toggleExtractIngredientsButtonVisibility() {
        if (ingredients.getText().toString().isEmpty()) {
            extractIngredients.setVisibility(View.VISIBLE);
        } else {
            extractIngredients.setVisibility(View.GONE);
        }
    }

    /**
     * adds all the fields to the query map even those which are null or empty.
     */
    public void getAllDetails(Map<String, String> targetMap) {
        traces.chipifyAllUnterminatedTokens();
        if (activity instanceof AddProductActivity) {
            String languageCode = ((AddProductActivity) activity).getProductLanguage();
            String lc = (!languageCode.isEmpty()) ? languageCode : "en";
            targetMap.put(PARAM_INGREDIENTS + "_" + lc, ingredients.getText().toString());
            List<String> list = traces.getChipValues();
            String string = StringUtil.join(list, ",");
            targetMap.put(PARAM_TRACES.substring(4), string);
        }
    }

    /**
     * adds only those fields to the query map which are not empty.
     */
    public void getDetails() {
        traces.chipifyAllUnterminatedTokens();
        if (activity instanceof AddProductActivity) {
            if (!ingredients.getText().toString().isEmpty()) {
                String languageCode = ((AddProductActivity) activity).getProductLanguage();
                String lc = (!languageCode.isEmpty()) ? languageCode : "en";
                ((AddProductActivity) activity).addToMap(PARAM_INGREDIENTS + "_" + lc, ingredients.getText().toString());
            }
            if (!traces.getChipValues().isEmpty()) {
                List<String> list = traces.getChipValues();
                String string = StringUtil.join(list, ",");
                ((AddProductActivity) activity).addToMap(PARAM_TRACES, string);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                if(result!=null && result.getUri().getPath()!=null) {
                    Uri resultUri = result.getUri();
                    imagePath = resultUri.getPath();
                    newImageSelected = true;
                    photoFile = new File((resultUri.getPath()));
                    ProductImage image = new ProductImage(code, INGREDIENTS, photoFile);
                    image.setFilePath(resultUri.getPath());
                    if (activity instanceof AddProductActivity) {
                        ((AddProductActivity) activity).addToPhotoMap(image, 1);
                    }
                }else{
                    Log.e(AddProductIngredientsFragment.class.getSimpleName(),"Crop image result path not found "+result.getUri());
                }
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Log.e(AddProductIngredientsFragment.class.getSimpleName(),"Crop image error: "+ result.getError().toString());
            }
        }

        EasyImage.handleActivityResult(requestCode, resultCode, data, getActivity(), new DefaultCallback() {
            @Override
            public void onImagePickerError(Exception e, EasyImage.ImageSource source, int type) {
            }

            @Override
            public void onImagesPicked(List<File> imageFiles, EasyImage.ImageSource source, int type) {
                CropImage.activity(Uri.fromFile(imageFiles.get(0)))
                    .setAllowFlipping(false)
                    .setCropMenuCropButtonIcon(R.drawable.ic_check_white_24dp)
                    .setOutputUri(Utils.getOutputPicUri(getContext()))
                    .start(activity.getApplicationContext(), AddProductIngredientsFragment.this);
            }
        });
    }

    public void showImageProgress() {
        imageProgress.setVisibility(View.VISIBLE);
        imageProgressText.setVisibility(View.VISIBLE);
        imageProgressText.setText(R.string.toastSending);
        imageIngredients.setVisibility(View.INVISIBLE);
    }

    public void hideImageProgress(boolean errorInUploading, String message) {
        imageProgress.setVisibility(View.INVISIBLE);
        imageProgressText.setVisibility(View.GONE);
        imageIngredients.setVisibility(View.VISIBLE);
        if (!errorInUploading) {
            Picasso.with(activity)
                .load(photoFile)
                .resize(dps50ToPixels(), dps50ToPixels())
                .centerInside()
                .into(imageIngredients);
            imageProgressText.setText(message);
            imageProgressText.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        }
    }

    public void setIngredients(String status, String ocrResult) {
        if (getActivity()!= null && !getActivity().isFinishing()) {
            switch (status) {
                case "set":
                    ingredients.setText(ocrResult);
                    break;
                case "0":
                    ingredients.setText(ocrResult);
                    btnLooksGood.setVisibility(View.VISIBLE);
                    btnSkipIngredients.setVisibility(View.VISIBLE);
                    break;
                default:
                    Toast.makeText(activity, R.string.unable_to_extract_ingredients, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    public void showOCRProgress() {
        extractIngredients.setVisibility(View.GONE);
        ingredients.setText(null);
        ocrProgress.setVisibility(View.VISIBLE);
        ocrProgressText.setVisibility(View.VISIBLE);
    }

    public void hideOCRProgress() {
        ocrProgress.setVisibility(View.GONE);
        ocrProgressText.setVisibility(View.GONE);
    }

    private int dps50ToPixels() {
        return dpsToPixels(50);
    }
}
